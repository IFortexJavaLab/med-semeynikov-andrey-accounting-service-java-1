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
import com.ifortex.internship.authservice.stripe.exception.StripeServiceException;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.*;
import com.ifortex.internship.authserviceapi.dto.response.*;
import com.ifortex.internship.usermanagementapi.UserManagementApi;
import com.ifortex.internship.usermanagementapi.dto.request.AuthUserForUserManagementDto;
import com.ifortex.internship.usermanagementapi.exception.CustomFeignException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import jakarta.mail.MessagingException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final RoleRepository roleRepository;
  private final EmailService emailService;
  private final RedisService redisService;
  private final UserManagementApi userManagementApi;
  private final CustomAuthenticationProvider authenticationProvider;
  private final TemporaryPasswordRepository passwordRepository;
  private final Environment environment;

  @Value("${app.otp.loginExpirationMinutes}")
  private int loginOtpExpirationMinutes;

  @Value("${app.tempPassword.expirationHours}")
  private int tempPasswordExpirationHours;

  @Transactional
  public void registerUser(RegistrationRequest request) {

    String userEmail = request.getEmail();

    log.debug("Register user: {}", userEmail);

    // todo feature change logic according to soft delete
    if (userRepository.findByEmail(userEmail).isPresent()) {
      log.debug("Email: {} is already registered.", userEmail);
      log.info("Failed to register user");
      throw new EmailAlreadyRegistered("Email: " + userEmail + " is already registered.");
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
            .findByName(UserRole.ROLE_USER)
            .map(List::of)
            .orElseGet(Collections::emptyList);

    User user =
        new User()
            .setUserId(UUID.randomUUID().toString())
            .setEmail(userEmail)
            .setPassword(hashedPassword)
            .setRoles(roles)
            .setCreatedAt(LocalDateTime.now())
            .setUpdatedAt(LocalDateTime.now());

    registerUserInStripe(user);

    userRepository.save(user);
    log.debug("User: {} saved to db successfully", userEmail);

    try {
      userManagementApi.saveUser(new AuthUserForUserManagementDto(user.getUserId()));
    } catch (CustomFeignException e) {
      log.debug(
          "Error occurred during call to the user management service. Details: {}", e.getMessage());
      throw new RegistrationFailedException(
          "Error occurred while registration. Please try again later");
    }

    log.info("User: {} register successfully", userEmail);
  }

  @Transactional
  public CreateUserResponse createUser(CreateUserRequest request) {
    return createUserWithRole(request.getEmail(), UserRole.ROLE_USER);
  }

  @Transactional
  public CreateUserResponse createAdmin(CreateAdminRequest request) {
    List<String> currentRoles = getUserRolesFromAuthentication();

    if (request.isSuperAdmin() && !currentRoles.contains(UserRole.ROLE_SUPER_ADMIN.name())) {
      throw new RegistrationFailedException("Only Super Admin can create another Admin.");
    }

    UserRole role = request.isSuperAdmin() ? UserRole.ROLE_SUPER_ADMIN : UserRole.ROLE_ADMIN;
    return createUserWithRole(request.getEmail(), role);
  }

  /**
   * Creates a new user with the specified role.
   *
   * @param email the email of the user to be created
   * @param role the role to assign to the user
   * @return a response containing a success message, temporary password, and its expiration time
   * @throws EmailAlreadyRegistered if the email is already registered
   * @throws RegistrationFailedException if the user role is not found or user creation fails
   */
  private CreateUserResponse createUserWithRole(String email, UserRole role) {
    log.debug("Creating user: {}", email);
    validateEmailNotRegistered(email);

    Role userRole = getRoleForUserType(role, email);

    String userId = UUID.randomUUID().toString();
    String password = generateTempPassword();
    String hashedPassword = passwordEncoder.encode(password);

    User user =
        new User()
            .setUserId(userId)
            .setEmail(email)
            .setRoles(Collections.singletonList(userRole))
            .setCreatedAt(LocalDateTime.now())
            .setUpdatedAt(LocalDateTime.now());

    TemporaryPassword temporaryPassword =
        new TemporaryPassword(
            user, hashedPassword, LocalDateTime.now().plusHours(tempPasswordExpirationHours));

    user.setTemporaryPassword(temporaryPassword);
    userRepository.save(user);

    log.debug("User: {} saved to db successfully", email);

    registerUserInUserManagementService(user);

    if (role.equals(UserRole.ROLE_USER)) {
      registerUserInStripe(user);
    }

    log.info("User: {} created successfully", email);
    return createUserResponse(user, password);
  }

  /**
   * Validates that the given email is not already registered.
   *
   * @param email the email to check
   * @throws EmailAlreadyRegistered if the email is already in use
   */
  private void validateEmailNotRegistered(String email) {
    if (userRepository.findByEmail(email).isPresent()) {
      log.debug("Email: {} is already registered.", email);
      throw new EmailAlreadyRegistered("Email: " + email + " is already registered.");
    }
  }

  /**
   * Retrieves the role entity for the specified user role.
   *
   * @param role the user role to retrieve
   * @param email the email of the user being registered (used for logging)
   * @return the corresponding Role entity
   * @throws RegistrationFailedException if the role is not found in the database
   */
  private Role getRoleForUserType(UserRole role, String email) {
    return roleRepository
        .findByName(role)
        .orElseThrow(
            () -> {
              log.debug("User Role: {} not found", role);
              return new RegistrationFailedException(
                  String.format("Failed to register user with email: %s", email));
            });
  }

  /**
   * Registers the newly created user in the UserManagement services.
   *
   * @param user the user to be registered
   * @throws RegistrationFailedException if the external service call fails
   */
  private void registerUserInUserManagementService(User user) {
    try {
      userManagementApi.saveUser(new AuthUserForUserManagementDto(user.getUserId()));
    } catch (CustomFeignException e) {
      log.debug(
          "Error occurred during call to the user management service. Details: {}", e.getMessage());
      throw new RegistrationFailedException(
          String.format("Failed to register with email: %s. Try again later", user.getEmail()));
    }
  }

  private CreateUserResponse createUserResponse(User user, String password) {
    String message = String.format("User: %s created successfully", user.getEmail());
    return new CreateUserResponse(message, password, tempPasswordExpirationHours);
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
        throw new EmailSendException(
            String.format("Failed to send 2FA verification email to the: %s", userEmail));
      }

      String verifyOtpLink = environment.getProperty("app.link.verifyOtpLogin");
      String message =
          String.format(
              "Two-factor authentication is required to complete your login. A verification code has been sent "
                  + "to your email: %s. Please enter the code along with your email at the following link: ",
              userEmail);
      return AuthResponse.builder().message(message).link(verifyOtpLink).build();
    }

    return buildAuthResponse(user);
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
      throw new InvalidRequestException("OTP has expired or is invalid. Please try again.");
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

    return buildAuthResponse(user);
  }

  @Transactional
  public AuthResponse logoutUser() {

    String userEmail = getUserEmailFromAuthentication();
    log.info("Logout attempt for user: {}", userEmail);
    refreshTokenRepository.deleteRefreshTokenByUserEmail(userEmail);
    log.debug("Refresh token deleted successfully for user: {}", userEmail);

    log.info("Logout successful for user: {}", userEmail);
    return AuthResponse.builder()
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
      throw new EmailSendException(
          String.format("Failed to send verification email to the: %s", email));
    }

    String resetPasswordLink = environment.getProperty("app.link.resetPasswordConfirm");
    String message =
        String.format(
            "An email with a password reset code has been sent to your email: %s, please follow this link: ",
            email);

    return new SuccessResponse(message, resetPasswordLink);
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
    log.debug("Initiating password reset for user with ID: {}", userId);

    List<String> currentUserRoles = getUserRolesFromAuthentication();
    boolean isCurrentUserSuperAdmin = currentUserRoles.contains(UserRole.ROLE_SUPER_ADMIN.name());

    User user =
        userRepository
            .findByUserId(userId)
            .orElseThrow(
                () -> {
                  log.debug("User with ID: {} not found", userId);
                  return new EntityNotFoundException(
                      String.format("User with ID: %s not found", userId));
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
    log.debug("Temporary password created and saved for user ID: {}", userId);

    refreshTokenRepository.deleteRefreshTokenByUserEmail(user.getEmail());
    log.debug("Refresh token deleted successfully for user: {}", user.getEmail());

    return new TemporaryPasswordResponse(tempPassword);
  }

  @Transactional
  public SuccessResponse resetPasswordWithEmail(String userId) {
    log.debug("Initiating password reset request email for user with ID: {}", userId);

    List<String> currentUserRoles = getUserRolesFromAuthentication();
    boolean isCurrentUserSuperAdmin = currentUserRoles.contains(UserRole.ROLE_SUPER_ADMIN.name());

    User user =
        userRepository
            .findByUserId(userId)
            .orElseThrow(
                () -> {
                  log.debug("User with ID: {} not found", userId);
                  return new EntityNotFoundException(
                      String.format("User with ID: %s not found", userId));
                });

    String userEmail = user.getEmail();

    checkSuperAdminModification(user, isCurrentUserSuperAdmin);
    deleteOldTemporaryPassword(user);

    user.setPassword(null);
    userRepository.save(user);
    log.debug("Password set to null for user ID: {}", userId);

    refreshTokenRepository.deleteRefreshTokenByUserEmail(userEmail);
    log.debug("Refresh token deleted successfully for user: {}", userEmail);

    String resetLinkTemplate = environment.getProperty("app.link.resetPasswordEmail", String.class);
    assert resetLinkTemplate != null;
    String resetLink = String.format(resetLinkTemplate, userEmail);
    try {
      emailService.sendPasswordResetRequestEmail(userEmail, "Password reset", resetLink);
    } catch (MessagingException e) {
      log.error(
          "Error during sending 2FA verification email for: {}. StackTrace: {}",
          userEmail,
          e.getMessage());
      throw new EmailSendException(
          String.format("Failed to send Password reset request to the email: %s", userEmail));
    }

    String message = String.format("Password reset request email sent to: %s.", userEmail);
    return new SuccessResponse(message);
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
   * @param user the user entity
   * @return an AuthResponse containing access and refresh token
   */
  private AuthResponse buildAuthResponse(User user) {

    String newAccessToken = tokenService.generateAccessToken(user);
    log.debug("Access token generated successfully for user: {}", user.getEmail());

    RefreshToken newRefreshToken = tokenService.createRefreshToken(user.getEmail());

    Integer jwtExpirationMs = Integer.valueOf(environment.getProperty("app.jwtExpirationMs"));
    Integer refreshTokenExpirationS =
        Integer.valueOf(environment.getProperty("app.refreshTokenExpirationS"));

    return AuthResponse.builder()
        .tokens(
            new TokensResponse(
                newAccessToken,
                newRefreshToken.getToken(),
                jwtExpirationMs,
                refreshTokenExpirationS))
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

  /**
   * Registers a user in Stripe by creating a customer record using the provided user object. If
   * successful, assigns the generated Stripe customer ID to the user entity.
   *
   * @param user The {@link User} entity for which a Stripe customer ID will be generated.
   * @throws StripeServiceException if an error occurs during communication with Stripe API.
   */
  private void registerUserInStripe(User user) {
    CustomerCreateParams customerParams =
        CustomerCreateParams.builder().setEmail(user.getEmail()).build();
    Customer customer;
    try {
      customer = Customer.create(customerParams);
    } catch (StripeException e) {
      log.error(
          "Stripe API call failed: {}. Error code: {}. StackTrace: ",
          e.getMessage(),
          e.getCode(),
          e);
      throw new StripeServiceException("Error occurred while registration. Please try again later");
    }
    user.setStripeCustomerId(customer.getId());
    log.debug(
        "Generated and saved StripeCustomerId: {} for user with ID: {}",
        customer.getId(),
        user.getUserId());
  }
}
