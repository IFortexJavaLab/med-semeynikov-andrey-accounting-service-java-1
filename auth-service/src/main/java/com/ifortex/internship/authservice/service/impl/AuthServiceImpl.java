package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.email.EmailService;
import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EmailAlreadyRegistered;
import com.ifortex.internship.authservice.exception.custom.EmailSendException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.InvalidRequestException;
import com.ifortex.internship.authservice.exception.custom.RegistrationFailedException;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.model.Role;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.model.UserDetailsImpl;
import com.ifortex.internship.authservice.model.constant.RedisKeyPrefix;
import com.ifortex.internship.authservice.model.constant.UserRole;
import com.ifortex.internship.authservice.repository.RefreshTokenRepository;
import com.ifortex.internship.authservice.repository.RoleRepository;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.CookieService;
import com.ifortex.internship.authservice.service.RedisService;
import com.ifortex.internship.authservice.service.TokenService;
import com.ifortex.internship.authserviceapi.dto.request.LoginRequest;
import com.ifortex.internship.authserviceapi.dto.request.PasswordResetRequest;
import com.ifortex.internship.authserviceapi.dto.request.PasswordResetWithOtpDto;
import com.ifortex.internship.authserviceapi.dto.request.RegistrationRequest;
import com.ifortex.internship.authserviceapi.dto.request.VerifyLoginOtpRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.CookieTokensResponse;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import com.ifortex.internship.usermanagementapi.UserManagementApi;
import com.ifortex.internship.usermanagementapi.dto.request.AuthUserForUserManagementDto;
import com.ifortex.internship.usermanagementapi.exception.CustomFeignException;
import jakarta.mail.MessagingException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final UserRepository userRepository;
  private final TokenService tokenService;
  private final AuthenticationManager authenticationManager;
  private final CookieService cookieService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final RoleRepository roleRepository;
  private final EmailService emailService;
  private final RedisService redisService;
  private final UserManagementApi userManagementApi;
  private final Environment environment;

  @Getter
  @Value("${app.otp.expirationMinutes}")
  private int expirationMinutes;

  @Transactional
  public SuccessResponse registerUser(RegistrationRequest request) {

    log.debug("Register user: {}", request.getEmail());

    // feature change logic according to soft delete
    if (userRepository.findByEmail(request.getEmail()).isPresent()) {
      log.debug("Email: {} is already registered.", request.getEmail());
      log.info("Failed to register user");
      throw new EmailAlreadyRegistered("Email: " + request.getEmail() + " is already registered.");
    }

    boolean passwordMismatch = !request.getPassword().equals(request.getPasswordConfirmation());
    if (passwordMismatch) {
      log.debug("Password and confirmation password do not match.");
      log.info("Failed to register user");
      throw new InvalidRequestException("Password and confirmation password do not match.");
    }

    String hashedPassword = passwordEncoder.encode(request.getPassword());

    List<Role> roles =
        roleRepository
            .findByName(UserRole.ROLE_NON_SUBSCRIBED_USER)
            .map(List::of)
            .orElseGet(Collections::emptyList);

    User user =
        new User()
            .setUserId(UUID.randomUUID().toString())
            .setEmail(request.getEmail())
            .setPassword(hashedPassword)
            .setRoles(roles)
            .setCreatedAt(LocalDateTime.now())
            .setUpdatedAt(LocalDateTime.now());
    userRepository.save(user);
    log.debug("User: {} saved to db successfully", request.getEmail());

    try {
      userManagementApi.saveUser(new AuthUserForUserManagementDto(user.getUserId()));
    } catch (CustomFeignException e) {
      log.debug(
          "Error occurred during call to the user management service. Details: {}", e.getMessage());
      throw new RegistrationFailedException(
          String.format("Failed to register with email: %s. Try again later", user.getEmail()));
    }

    log.info("User: {} register successfully", request.getEmail());

    String message =
        String.format("User with email: %s has been successfully registered", user.getEmail());

    return new SuccessResponse(message);
  }

  public AuthResponse authenticateUser(LoginRequest loginRequest) {

    String userEmail = loginRequest.getEmail();
    log.debug("Authenticating user with email: {}", userEmail);

    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(userEmail, loginRequest.getPassword()));

    SecurityContextHolder.getContext().setAuthentication(authentication);
    log.debug("User: {} successfully authenticated.", userEmail);

    UserDetailsImpl user = (UserDetailsImpl) authentication.getPrincipal();

    if (user.isTwoFactorEnabled()) {
      log.debug("User: {} has 2FA enabled. Sending OTP", userEmail);

      String otp = generateOtp();
      String redisKey = RedisKeyPrefix.LOGIN_OTP.getPrefix() + userEmail;
      redisService.saveOtp(redisKey, otp, expirationMinutes);
      log.debug("Otp for user: {} generated and saved successfully", userEmail);

      // feature refactor method with dotry
      try {
        emailService.sendVerificationEmail(userEmail, "2FA Verification Code", otp);
      } catch (MessagingException e) {
        log.error(
            "Error during sending 2FA verification email for: {}. StackTrace: {}",
            userEmail,
            e.getMessage());
        throw new EmailSendException("Failed to send 2FA verification email");
      }

      String verifyOtpLink = environment.getProperty("app.link.verifyOtpLogin");
      String message =
          String.format(
              "Two-factor authentication is required to complete your login. A verification code has been sent "
                  + "to your email: %s. Please enter the code along with your email at the following link: %s",
              userEmail, verifyOtpLink);
      return AuthResponse.builder().message(message).build();
    }

    List<String> roles =
        user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());

    return buildAuthResponse(userEmail, roles, user.getUserId());
  }

  public AuthResponse completeLoginWithOtp(VerifyLoginOtpRequest request) {

    String userEmail = request.getEmail();
    log.debug("Verifying otp to log in for email: {}", userEmail);

    String otpFromRequest = request.getOtp();
    String redisKey = RedisKeyPrefix.LOGIN_OTP.getPrefix() + userEmail;
    String storedOtp = redisService.getOtp(redisKey);

    if (!otpFromRequest.equals(storedOtp)) {
      log.debug("OTP has expired or is invalid for email: {}", userEmail);
      log.info("Failed to reset password for user: {}", userEmail);
      throw new AuthorizationException("OTP has expired or is invalid. Please try again.");
    }

    var user =
        userRepository
            .findByEmail(userEmail)
            .orElseThrow(
                () -> {
                  log.debug("User with email: {} not found", userEmail);
                  return new AuthorizationException("Failed to verify otp. Please try again.");
                });

    List<String> roles =
        user.getRoles().isEmpty()
            ? List.of(UserRole.ROLE_NON_SUBSCRIBED_USER.name())
            : user.getRoles().stream().map(role -> role.getName().name()).toList();

    return buildAuthResponse(userEmail, roles, user.getUserId());
  }

  @Transactional
  public AuthResponse logoutUser() {

    Object principle = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    UserDetailsImpl userDetails;
    if (!"anonymousUser".equals(principle.toString())) {
      userDetails = (UserDetailsImpl) principle;
      log.debug("Deleting refresh token for user: {}", userDetails.getUsername());
      refreshTokenRepository.deleteRefreshTokenByUserEmail(userDetails.getEmail());
      log.debug("Refresh token deleted successfully for user: {}", userDetails.getUsername());
    } else {
      log.debug("Logout attempt by anonymous or unauthenticated user.");
      throw new AuthorizationException("User is not authenticated. Please log in.");
    }

    ResponseCookie accessTokenCookie = cookieService.deleteAccessTokenCookie();
    ResponseCookie refreshTokenCookie = cookieService.deleteRefreshTokenCookie();

    return AuthResponse.builder()
        .cookieTokensResponse(new CookieTokensResponse(accessTokenCookie, refreshTokenCookie))
        .email(userDetails.getUsername())
        .message(String.format("Logout successful for user %s", userDetails.getUsername()))
        .build();
  }

  public SuccessResponse initiatePasswordReset(PasswordResetRequest passwordResetRequest) {

    String userEmail = passwordResetRequest.getEmail();
    log.debug("Initiating password reset for email: {}", userEmail);

    if (userRepository.findByEmail(userEmail).isEmpty()) {
      log.debug("User with email: {} not found", userEmail);
      throw new EntityNotFoundException(String.format("User with email: %s not found", userEmail));
    }

    String otp = generateOtp();
    log.debug("Otp for user: {} generated successfully", userEmail);

    String redisKey = RedisKeyPrefix.PASSWORD_RESET.getPrefix() + userEmail;
    redisService.saveOtp(redisKey, otp, expirationMinutes);
    log.debug("Otp saved to db successfully for user: {}", userEmail);

    try {
      emailService.sendVerificationEmail(userEmail, "Password reset", otp);
    } catch (MessagingException e) {
      log.error(
          "Error during sending verification email for: {}. There details: {}",
          userEmail,
          e.getMessage());
      throw new EmailSendException("Failed to send verification email");
    }

    String resetPasswordLink = environment.getProperty("app.link.resetPassword");
    String message =
        String.format(
            "An email with a password reset code has been sent to your email: %s, please follow this link: %s",
            userEmail, resetPasswordLink);

    return new SuccessResponse(message);
  }

  public SuccessResponse resetPasswordWithOtp(PasswordResetWithOtpDto request) {

    String userEmail = request.getEmail();
    log.debug("Reset password with otp started for user: {}", userEmail);

    String otpFromRequest = request.getOtp();
    String redisKey = RedisKeyPrefix.PASSWORD_RESET.getPrefix() + userEmail;
    String storedOtp = redisService.getOtp(redisKey);

    if (!otpFromRequest.equals(storedOtp)) {
      log.debug("OTP has expired or is invalid for email: {}", userEmail);
      log.info("Failed to reset password for user: {}", userEmail);
      throw new AuthorizationException("Invalid OTP provided. Please try again.");
    }

    boolean passwordMismatch = !request.getNewPassword().equals(request.getPasswordConfirmation());
    if (passwordMismatch) {
      log.debug("Password and confirmation password do not match.");
      log.info("Failed to reset password for user: {}", userEmail);
      throw new InvalidRequestException("Password and confirmation password do not match.");
    }

    var user =
        userRepository
            .findByEmail(userEmail)
            .orElseThrow(
                () -> {
                  log.debug("User with email: {} not found", userEmail);
                  return new AuthorizationException("Failed to reset password. Please try again.");
                });

    String newEncodedPassword = passwordEncoder.encode(request.getNewPassword());
    user.setPassword(newEncodedPassword);
    user.setUpdatedAt(LocalDateTime.now());
    userRepository.save(user);

    redisService.deleteOtp(redisKey);

    log.info("User with email: {} successfully changed password", userEmail);

    String loginLink = environment.getProperty("app.link.login");

    return new SuccessResponse(
        String.format(
            "Changed password successfully for user with email %s, please log in again using this link: %s",
            user.getEmail(), loginLink));
  }

  /**
   * Constructs an {@link AuthResponse} containing authentication tokens and cookies for the
   * specified user.
   *
   * <p>This method generates a new access token and refresh token for the user, creates cookies to
   * store the tokens, and packages them into an AuthResponse.
   *
   * @param userEmail the email of the authenticated user
   * @param roles the roles assigned to the user
   * @return an AuthResponse containing access and refresh token cookies, as well as a success
   *     message
   */
  private AuthResponse buildAuthResponse(String userEmail, List<String> roles, String userId) {

    String newAccessToken = tokenService.generateAccessToken(userEmail, roles, userId);
    log.debug("Access token generated successfully for user: {}", userEmail);

    RefreshToken newRefreshToken = tokenService.createRefreshToken(userEmail);

    ResponseCookie accessTokenCookie = cookieService.createAccessTokenCookie(newAccessToken);
    ResponseCookie refreshTokenCookie =
        cookieService.createRefreshTokenCookie(newRefreshToken.getToken());
    log.debug(
        "Cookies with access and refresh tokens generated successfully for user: {}", userEmail);

    return AuthResponse.builder()
        .cookieTokensResponse(new CookieTokensResponse(accessTokenCookie, refreshTokenCookie))
        .email(userEmail)
        .message(String.format("Login successful for user: %s.", userEmail))
        .build();
  }

  /**
   * Generates a random 6-digit one-time password (OTP) for authentication purposes.
   *
   * @return a 6-digit OTP as a String
   */
  private String generateOtp() {
    Random random = new Random();
    int code = random.nextInt(900000) + 100000;
    return String.valueOf(code);
  }
}
