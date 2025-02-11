package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.email.EmailService;
import com.ifortex.internship.authservice.exception.custom.*;
import com.ifortex.internship.authservice.model.*;
import com.ifortex.internship.authservice.model.constant.RedisKeyPrefix;
import com.ifortex.internship.authservice.model.constant.UserRole;
import com.ifortex.internship.authservice.repository.RefreshTokenRepository;
import com.ifortex.internship.authservice.repository.RoleRepository;
import com.ifortex.internship.authservice.repository.TemporaryPasswordRepository;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.RedisService;
import com.ifortex.internship.authservice.service.TokenService;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.CreateUserRequest;
import com.ifortex.internship.authserviceapi.dto.request.LoginRequest;
import com.ifortex.internship.authserviceapi.dto.request.PasswordResetWithOtpDto;
import com.ifortex.internship.authserviceapi.dto.request.RegistrationRequest;
import com.ifortex.internship.authserviceapi.dto.request.VerifyLoginOtpRequest;
import com.ifortex.internship.authserviceapi.dto.response.*;
import com.ifortex.internship.usermanagementapi.UserManagementApi;
import com.ifortex.internship.usermanagementapi.dto.request.AuthUserForUserManagementDto;
import com.ifortex.internship.usermanagementapi.exception.CustomFeignException;
import jakarta.mail.MessagingException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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
  private final CustomAuthenticationProvider authenticationProvider;
  private final TemporaryPasswordRepository passwordRepository;
  private final Environment environment;

  private static final Set<String> VALID_ROLES =
      Arrays.stream(UserRole.values()).map(Enum::name).collect(Collectors.toSet());

  @Value("${app.otp.loginExpirationMinutes}")
  private int loginOtpExpirationMinutes;

  @Value("${app.tempPassword.expirationHours}")
  private int tempPasswordExpirationHours;

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

  @Transactional
  public CreateUserResponse createUser(CreateUserRequest request) {

    log.debug("Creating user: {}", request.getEmail());

    // feature change logic according to soft delete
    if (userRepository.findByEmail(request.getEmail()).isPresent()) {
      log.debug("Email: {} is already registered.", request.getEmail());
      log.info("Failed to create user");
      throw new EmailAlreadyRegistered("Email: " + request.getEmail() + " is already registered.");
    }

    boolean isCreatingParamedic = request.getRoles().contains(UserRole.ROLE_PARAMEDIC.name());
    if (isCreatingParamedic) {
      log.debug("Creating user with role PARAMEDIC");
      // todo make request to the paramedic service and save bonus policy and user id there
      // now it is mocked
      return null;
    }

    String userId = UUID.randomUUID().toString();

    List<Role> roles =
        request.getRoles().stream()
            .filter(VALID_ROLES::contains)
            .map(UserRole::valueOf)
            .map(roleRepository::findByName)
            .flatMap(Optional::stream)
            .collect(Collectors.toList());

    User user = new User().setUserId(userId).setEmail(request.getEmail()).setRoles(roles);

    String password = generateTempPassword();
    String hashedPassword = passwordEncoder.encode(password);

    TemporaryPassword temporaryPassword =
        new TemporaryPassword(
            user, hashedPassword, LocalDateTime.now().plusHours(tempPasswordExpirationHours));

    user.setTemporaryPassword(temporaryPassword)
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

    log.info("User: {} created successfully", request.getEmail());

    String message = String.format("User: %s created successfully", request.getEmail());
    return new CreateUserResponse(message, password);
  }

  public AuthResponse authenticateUser(LoginRequest loginRequest) {

    String userEmail = loginRequest.getEmail();
    log.debug("Authenticating user with email: {}", userEmail);

    Authentication authentication =
        authenticationProvider.authenticate(
            new UsernamePasswordAuthenticationToken(userEmail, loginRequest.getPassword()));

    SecurityContextHolder.getContext().setAuthentication(authentication);
    log.debug("User: {} successfully authenticated.", userEmail);
    User user = (User) authentication.getPrincipal();

    if (user.isTwoFactorEnabled()) {
      log.debug("User: {} has 2FA enabled. Sending OTP", userEmail);

      String otp = generateOtp();
      String redisKey = RedisKeyPrefix.LOGIN_OTP.getPrefix() + userEmail;
      redisService.saveOtp(redisKey, otp, loginOtpExpirationMinutes);
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
        user.getRoles().stream().map(role -> role.getName().name()).collect(Collectors.toList());

    return buildAuthResponse(userEmail, roles, user.getUserId());
  }

  @Transactional
  public AuthResponse completeLoginWithOtp(VerifyLoginOtpRequest request) {

    String userEmail = request.getEmail();
    log.debug("Verifying otp to log in for email: {}", userEmail);

    String otpFromRequest = request.getOtp();
    String redisKey = RedisKeyPrefix.LOGIN_OTP.getPrefix() + userEmail;
    String storedOtp = redisService.getOtp(redisKey);

    if (!otpFromRequest.equals(storedOtp)) {
      log.debug("OTP has expired or is invalid for email: {}", userEmail);
      log.info("Failed to login for user: {}", userEmail);
      throw new AuthorizationException("OTP has expired or is invalid. Please try again.");
    }

    redisService.deleteOtp(redisKey);

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

    String userEmail = getUserEmailFromAuthentication();
    log.debug("Deleting refresh token for user: {}", userEmail);
    refreshTokenRepository.deleteRefreshTokenByUserEmail(userEmail);
    log.debug("Refresh token deleted successfully for user: {}", userEmail);

    /*ResponseCookie accessTokenCookie = cookieService.deleteAccessTokenCookie();
    ResponseCookie refreshTokenCookie = cookieService.deleteRefreshTokenCookie();*/

    return AuthResponse.builder()
        /*.cookieTokensResponse(new CookieTokensResponse(accessTokenCookie, refreshTokenCookie))*/
        .email(userEmail)
        .message(String.format("Logout successful for user %s", userEmail))
        .build();
  }

  public SuccessResponse initiatePasswordReset(String email) {

    log.debug("Initiating password reset for email: {}", email);

    if (userRepository.findByEmail(email).isEmpty()) {
      log.debug("User with email: {} not found", email);
      throw new EntityNotFoundException(String.format("User with email: %s not found", email));
    }

    String otp = generateOtp();
    log.debug("Otp for user: {} generated successfully", email);

    String redisKey = RedisKeyPrefix.PASSWORD_RESET.getPrefix() + email;
    redisService.saveOtp(redisKey, otp, loginOtpExpirationMinutes);
    log.debug("Otp saved to db successfully for user: {}", email);

    try {
      emailService.sendVerificationEmail(email, "Password reset", otp);
    } catch (MessagingException e) {
      log.error(
          "Error during sending verification email for: {}. There details: {}",
          email,
          e.getMessage());
      throw new EmailSendException("Failed to send verification email");
    }

    String resetPasswordLink = environment.getProperty("app.link.resetPassword");
    String message =
        String.format(
            "An email with a password reset code has been sent to your email: %s, please follow this link: ",
            email);

    return new SuccessResponse(message);
  }

  @Transactional
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
    user.setTemporaryPassword(null);
    user.setUpdatedAt(LocalDateTime.now());
    userRepository.save(user);

    redisService.deleteOtp(redisKey);

    log.info("User with email: {} successfully changed password", userEmail);

    String loginLink = environment.getProperty("app.link.login");
    String message =
        String.format(
            "Changed password successfully for user with email %s, please log in again using this link: ",
            user.getEmail());
    return new SuccessResponse(message, loginLink);
  }

  public String generateOtp() {
    Random random = new Random();
    int code = random.nextInt(900000) + 100000;
    return String.valueOf(code);
  }

  public String getUserEmailFromAuthentication() {
    Object principle = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if ("anonymousUser".equals(principle.toString())) {
      log.debug("Attempt to get user details by anonymous or unauthenticated user.");
      throw new AuthorizationException("User is not authenticated. Please log in.");
    }
    String userEmail = ((UserDetailsImpl) principle).getEmail();
    return userEmail;
  }

  public String getUserIdFromAuthentication() {
    Object principle = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if ("anonymousUser".equals(principle.toString())) {
      log.debug("Attempt to get user details by anonymous or unauthenticated user.");
      throw new AuthorizationException("User is not authenticated. Please log in.");
    }
    String userId = ((UserDetailsImpl) principle).getUserId();
    return userId;
  }

  public List<String> getUserRolesFromAuthentication() {
    Object principle = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if ("anonymousUser".equals(principle.toString())) {
      log.debug("Attempt to get user details by anonymous or unauthenticated user.");
      throw new AuthorizationException("User is not authenticated. Please log in.");
    }
    List<String> userRoles =
        ((UserDetailsImpl) principle)
            .getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    return userRoles;
  }

  public List<AuthUserDto> searchUsers(
      List<String> userIds, List<String> roles, String status, String email) {
    List<User> users = userRepository.findByUserIdIn(userIds);

    Predicate<User> userFilter =
        filterByRoles(roles).and(filterByStatus(status)).and(filterByEmail(email));

    return users.stream()
        .filter(userFilter)
        .map(this::convertToAuthUserDto)
        .collect(Collectors.toList());
  }

  @Transactional
  public TemporaryPasswordResponse resetPasswordWithTemp(String userId) {
    List<String> currentUserRoles = getUserRolesFromAuthentication();
    boolean isCurrentUserSuperAdmin = currentUserRoles.contains(UserRole.ROLE_SUPER_ADMIN.name());

    User user =
        userRepository
            .findByUserId(userId)
            .orElseThrow(
                () -> {
                  log.debug("User with ID: {} not found", userId);
                  return new EntityNotFoundException(
                      String.format("User with email: %s not found", userId));
                });

    checkSuperAdminModification(user, isCurrentUserSuperAdmin);

    deleteOldTemporaryPassword(user);

    String tempPassword = generateTempPassword();
    String hashedPassword = passwordEncoder.encode(tempPassword);
    var temporaryPassword =
        new TemporaryPassword(
            user, hashedPassword, LocalDateTime.now().plusHours(tempPasswordExpirationHours));

    user.setPassword(null);
    user.setTemporaryPassword(temporaryPassword);
    userRepository.save(user);

    refreshTokenRepository.deleteRefreshTokenByUserEmail(user.getEmail());
    log.debug("Refresh token deleted successfully for user: {}", user.getEmail());

    return new TemporaryPasswordResponse(tempPassword);
  }

  /**
   * Checks if the current user has permission to modify a user with the ROLE_SUPER_ADMIN role.
   * Throws an exception if the current user is not a super admin and attempts to modify a super
   * admin.
   *
   * @param user the user to be modified
   * @param isCurrentUserSuperAdmin whether the current user is a super admin
   * @throws SuperAdminModificationException if the current user is not a super admin and tries to
   *     modify a super admin
   */
  private void checkSuperAdminModification(User user, boolean isCurrentUserSuperAdmin) {
    boolean isEditedUserSuperAdmin =
        user.getRoles().stream().anyMatch(role -> role.getName() == UserRole.ROLE_SUPER_ADMIN);

    if (isEditedUserSuperAdmin && !isCurrentUserSuperAdmin) {
      log.debug("Attempt to reset password for ROLE_SUPER_ADMIN with ROLE_ADMIN");
      throw new SuperAdminModificationException("Access denied");
    }
  }

  /**
   * Deletes the old temporary password for the specified user, if it exists. Sets the user's
   * temporary password to null after deletion.
   *
   * @param user the user whose old temporary password will be deleted
   */
  private void deleteOldTemporaryPassword(User user) {
    if (user.getTemporaryPassword() != null) {
      passwordRepository.delete(user.getTemporaryPassword());
      user.setTemporaryPassword(null);
      passwordRepository.flush();
      log.debug("Deleted previous temp password for user ID: {}", user.getUserId());
    }
  }

  /**
   * Constructs an {@link AuthResponse} containing authentication tokens for the specified user.
   *
   * <p>This method generates a new access token and refresh token for the user and packages them
   * into an AuthResponse.
   *
   * @param userEmail the email of the authenticated user
   * @param roles the roles assigned to the user
   * @return an AuthResponse containing access and refresh token
   */
  private AuthResponse buildAuthResponse(String userEmail, List<String> roles, String userId) {

    String newAccessToken = tokenService.generateAccessToken(userEmail, roles, userId);
    log.debug("Access token generated successfully for user: {}", userEmail);

    RefreshToken newRefreshToken = tokenService.createRefreshToken(userEmail);

    /* ResponseCookie accessTokenCookie = cookieService.createAccessTokenCookie(newAccessToken);
    ResponseCookie refreshTokenCookie =
        cookieService.createRefreshTokenCookie(newRefreshToken.getToken());
    log.debug(
        "Cookies with access and refresh tokens generated successfully for user: {}", userEmail);*/

    return AuthResponse.builder()
        .tokens(new TokensResponse(newAccessToken, newRefreshToken.getToken()))
        /*.cookieTokensResponse(new CookieTokensResponse(accessTokenCookie, refreshTokenCookie))*/
        .message(String.format("Login successful for user: %s.", userEmail))
        .build();
  }

  /**
   * Creates a predicate to filter users by their roles.
   *
   * <p>If the {@code roles} parameter is null, no filtering is applied. Otherwise, the predicate
   * checks if the user's roles match any of the provided roles.
   *
   * @param roles List of roles to filter users by (e.g., "ADMIN", "USER"). Can be null.
   * @return A {@link Predicate} that filters users based on roles.
   */
  private Predicate<User> filterByRoles(List<String> roles) {
    return user ->
        roles == null
            || user.getRoles().stream().anyMatch(role -> roles.contains(role.getName().name()));
  }

  /**
   * Creates a predicate to filter users by their status.
   *
   * <p>If the {@code status} parameter is null, no filtering is applied. Otherwise, the predicate
   * checks if the user's status matches the provided status (case-insensitive).
   *
   * @param status User status to filter by (e.g., "ACTIVE", "BLOCKED"). Can be null.
   * @return A {@link Predicate} that filters users based on their status.
   */
  private Predicate<User> filterByStatus(String status) {
    return user -> status == null || user.getStatus().name().equalsIgnoreCase(status);
  }

  /**
   * Creates a predicate to filter users by their email address.
   *
   * <p>If the {@code email} parameter is null, no filtering is applied. Otherwise, the predicate
   * performs a case-insensitive partial match to filter users by email.
   *
   * @param email Email address (or part of it) to filter users by. Can be null.
   * @return A {@link Predicate} that filters users based on their email.
   */
  private Predicate<User> filterByEmail(String email) {
    return user -> email == null || user.getEmail().toLowerCase().contains(email.toLowerCase());
  }

  /**
   * Converts a {@link User} entity to an {@link AuthUserDto}.
   *
   * <p>This method extracts relevant fields from the {@link User} entity, including user ID, email,
   * two-factor authentication status, soft-deletion status, user roles, and account status.
   *
   * @param user The {@link User} entity to convert. Cannot be null.
   * @return An {@link AuthUserDto} representing the user.
   */
  private AuthUserDto convertToAuthUserDto(User user) {
    List<String> roleNames =
        user.getRoles().stream()
            .map(Role::getName)
            .map(UserRole::name)
            .collect(Collectors.toList());

    return new AuthUserDto()
        .setUserId(user.getUserId())
        .setEmail(user.getEmail())
        .setTwoFactorEnabled(user.isTwoFactorEnabled())
        .setSoftDeleted(user.isSoftDeleted())
        .setRoles(roleNames)
        .setStatus(user.getStatus().name());
  }

  /**
   * Generates a temporary password that meets the following criteria:
   *
   * <ul>
   *   <li>Has a fixed length of 8 characters.
   *   <li>Contains at least one uppercase letter (A–Z).
   *   <li>Contains at least one digit (0–9).
   *   <li>Contains at least one special character (@, $, !, %, *, ?, &, #).
   *   <li>The remaining characters are randomly selected from uppercase letters, lowercase letters,
   *       digits, and special characters.
   * </ul>
   *
   * <p>The password is generated using a pseudorandom number generator.
   *
   * @return a randomly generated temporary password that meets the specified security requirements.
   */
  private String generateTempPassword() {
    String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    String DIGITS = "0123456789";
    String SPECIAL_CHARACTERS = "@$!%*?&#";
    String ALL_ALLOWED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$!%*?&#";

    Random random = new Random();
    int length = 8;

    char upper = UPPERCASE.charAt(random.nextInt(UPPERCASE.length()));
    char digit = DIGITS.charAt(random.nextInt(DIGITS.length()));
    char special = SPECIAL_CHARACTERS.charAt(random.nextInt(SPECIAL_CHARACTERS.length()));

    StringBuilder password = new StringBuilder();
    password.append(upper).append(digit).append(special);
    for (int i = 3; i < length; i++) {
      password.append(ALL_ALLOWED.charAt(random.nextInt(ALL_ALLOWED.length())));
    }

    log.debug("Temporary password generated successfully");
    return password.toString();
  }
}
