package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.email.EmailService;
import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EmailSendException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.ForbiddenActionException;
import com.ifortex.internship.authservice.exception.custom.InternalAuthServiceException;
import com.ifortex.internship.authservice.exception.custom.InvalidRequestException;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.model.constant.RedisKeyPrefix;
import com.ifortex.internship.authservice.model.constant.UserRole;
import com.ifortex.internship.authservice.model.mapper.UserMapper;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.RedisService;
import com.ifortex.internship.authservice.service.UserService;
import com.ifortex.internship.authservice.stripe.service.StripeService;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.BlockUserRequest;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.request.TwoFactorAuthRequest;
import com.ifortex.internship.authserviceapi.dto.request.UnblockUserRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.ChangeEmailResponse;
import com.ifortex.internship.usermanagementapi.UserManagementApi;
import com.ifortex.internship.usermanagementapi.dto.request.AuthUserForUserManagementDto;
import com.ifortex.internship.usermanagementapi.dto.request.DeleteUserRequest;
import com.ifortex.internship.usermanagementapi.exception.CustomFeignException;
import com.stripe.exception.StripeException;
import jakarta.mail.MessagingException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final StripeService stripeService;

  @Value("${app.otp.emailExpirationMinutes}")
  private int expirationMinutes;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthService authService;
  private final UserMapper userMapper;
  private final RedisService redisService;
  private final EmailService emailService;
  private final Environment environment;
  private final CustomAuthenticationProvider authenticationProvider;
  private final UserManagementApi userManagementApi;

  public AuthUserDto getUserByUserId(String userId) {
    log.debug("Getting user by userId: {}", userId);

    var user = findUserByUserId(userId);
    return userMapper.toDto(user);
  }

  public AuthUserDto getUser() {
    String userId = authService.getUserIdFromAuthentication();
    return getUserByUserId(userId);
  }

  public List<AuthUserDto> getAllUsers() {

    log.debug("Getting all users");

    List<User> users = userRepository.findAll();
    List<AuthUserDto> authUserDtoList = users.stream().map(userMapper::toDto).toList();

    return authUserDtoList;
  }

  @Transactional
  public AuthResponse changePassword(ChangePasswordRequest request, String userEmail) {

    log.debug("Changing password for user with email: {}", userEmail);

    User user = findUserByEmail(userEmail);

    // todo вот тут будет выкидываться ошибка 401 когдя я захочу сменить пароль и введу текущий
    // неверный. Но эта штука мне нужна чтобы не копировать authprovider который использует в том
    // числе временные пароли
    authenticationProvider.authenticate(
        new UsernamePasswordAuthenticationToken(userEmail, request.getCurrentPassword()));

    if (request.getCurrentPassword().equals(request.getNewPassword())) {
      log.debug(
          "Current password and new password are equal for user with email: {}", user.getEmail());
      throw new InvalidRequestException(
          String.format(
              "Current password and new password are equal for user with email: %s",
              user.getEmail()));
    }

    String newEncodedPassword = passwordEncoder.encode(request.getNewPassword());
    user.setPassword(newEncodedPassword);
    user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
    user.setTemporaryPassword(null);
    userRepository.save(user);

    log.info("User with email: {} successfully changed password", userEmail);

    String link = "http://localhost:8085/api/v1/auth/login";

    String message =
        String.format(
            "Changed password successfully for user with email %s, please log in again using this link: ",
            user.getEmail());

    AuthResponse authResponse = authService.logoutUser();
    authResponse.setMessage(message);
    authResponse.setLink(link);

    return authResponse;
  }

  @Transactional
  public ChangeEmailResponse changeEmail(String newEmail, String code) {

    String currentEmail = authService.getUserEmailFromAuthentication();

    if (currentEmail.equals(newEmail)) {
      log.debug("New provided email is equal to the current one for user: {}", currentEmail);
      throw new InvalidRequestException("New provided email is equal to the current one");
    }

    if (userRepository.findByEmail(newEmail).isPresent()) {
      log.debug("User with email: {} already registered in the system", newEmail);
      throw new InvalidRequestException("New email already registered in the system");
    }

    log.debug("Changing email for user: {}", currentEmail);

    if (code == null) {
      String otp = authService.generateOtp();
      log.debug("Otp for user: {} generated successfully", currentEmail);

      String redisKey = RedisKeyPrefix.EMAIL_CHANGE.getPrefix() + currentEmail;
      redisService.saveOtp(redisKey, otp, expirationMinutes);
      log.debug("Otp saved to db successfully for user: {}", currentEmail);

      try {
        emailService.sendVerificationEmail(currentEmail, "Email change", otp);
      } catch (MessagingException e) {
        log.error(
            "Error during sending verification email for: {}. There details: {}",
            currentEmail,
            e.getMessage());
        throw new EmailSendException("Failed to send verification email");
      }

      String changeEmailLink = environment.getProperty("app.link.changeEmail");
      String message =
          String.format(
              "An email with a change email code has been sent to your email: %s, please follow this link:",
              currentEmail);
      return new ChangeEmailResponse(message, changeEmailLink);
    }

    String redisKey = RedisKeyPrefix.EMAIL_CHANGE.getPrefix() + currentEmail;
    String storedOtp = redisService.getOtp(redisKey);

    if (!code.equals(storedOtp)) {
      log.debug("OTP has expired or is invalid for email: {}", currentEmail);
      log.info("Failed to change email for user: {}", currentEmail);
      throw new InvalidRequestException("OTP has expired or is invalid. Please try again.");
    }

    var user =
        userRepository
            .findByEmail(currentEmail)
            .orElseThrow(
                () -> {
                  log.debug("User with email: {} not found", currentEmail);
                  return new AuthorizationException("Failed to verify otp. Please try again.");
                });

    user.setEmail(newEmail);
    user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
    userRepository.save(user);
    redisService.deleteOtp(redisKey);

    AuthUserDto authUserDto = userMapper.toDto(user);
    String loginLink = environment.getProperty("app.link.login");
    String message =
        String.format(
            "Changed email successfully, please log in again using this link: %s", loginLink);

    return new ChangeEmailResponse(authUserDto, message, loginLink);
  }

  @Transactional
  public AuthUserDto changeTwoFactorAuth(TwoFactorAuthRequest request) {

    String email = authService.getUserEmailFromAuthentication();

    log.debug("Changing 2FA for user: {}", email);

    User savedUser = findUserByEmail(email);

    Boolean newTwoFactorState = request.getIsTwoFactorEnabled();
    if (newTwoFactorState == null) {
      return userMapper.toDto(savedUser);
    }

    if (newTwoFactorState == savedUser.isTwoFactorEnabled()) {
      log.debug("2FA state is the same for user: {}", email);
      return userMapper.toDto(savedUser);
    }

    log.info("Updating 2FA state for user: {}", email);
    savedUser.setTwoFactorEnabled(newTwoFactorState);
    savedUser.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
    userRepository.save(savedUser);

    return userMapper.toDto(savedUser);
  }

  @Transactional
  public AuthUserDto changeTwoFactorAuthByAdmin(String userId, TwoFactorAuthRequest request) {
    log.debug("Changing 2FA for user with id: {} by admin", userId);

    User savedUser = findUserByUserId(userId);

    Boolean newTwoFactorState = request.getIsTwoFactorEnabled();
    if (newTwoFactorState == null) {
      return userMapper.toDto(savedUser);
    }

    if (newTwoFactorState == savedUser.isTwoFactorEnabled()) {
      log.debug("2FA state is the same for user with id: {}", userId);
      return userMapper.toDto(savedUser);
    }

    log.info("Updating 2FA state for user with id: {}", userId);
    savedUser.setTwoFactorEnabled(newTwoFactorState);
    savedUser.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
    userRepository.save(savedUser);

    return userMapper.toDto(savedUser);
  }

  @Transactional
  public void blockUser(BlockUserRequest request) {

    log.debug("Blocking user with ID: {}", request.getUserId());

    var user = findUserByUserId(request.getUserId());

    validateSelfModification(user, "block");

    validateSuperAdminModificationPermission(user);

    user.setBlockedUntil(request.getExpiresAt());
    user.setRefreshToken(null);
    user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
    userRepository.save(user);

    log.debug("User with ID: {} blocked successfully", user.getUserId());
  }

  @Transactional
  public void unblockUser(UnblockUserRequest request) {

    log.debug("Unblocking user with ID: {}", request.getUserId());

    var user = findUserByUserId(request.getUserId());

    validateSelfModification(user, "unblock");

    validateSuperAdminModificationPermission(user);

    user.setBlockedUntil(null);
    user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
    userRepository.save(user);

    log.debug("User with ID: {} unblocked successfully", user.getUserId());
  }

  @Transactional
  public void softDeleteUser(String userId) {

    log.debug("Attempt to delete  user with ID: {} with soft delete", userId);

    var user = findUserByUserId(userId);

    validateSelfModification(user, "soft delete"); // feature convert to enum
    validateSuperAdminModificationPermission(user);

    user.setSoftDeleted(true);
    user.setRefreshToken(null);
    user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));

    userRepository.save(user);

    try {
      userManagementApi.saveUser(
          new AuthUserForUserManagementDto(user.getUserId(), user.isSoftDeleted()));
    } catch (CustomFeignException e) {
      log.debug(
          "Error occurred during call to the user management service. Details: {}", e.getMessage());
      throw new InternalAuthServiceException(
          "Error occurred while deleting. Please try again later");
    }

    log.debug("User user with ID: {} deleted successfully with soft delete", userId);
  }

  @Transactional
  public void hardDelete(String userId) {

    log.debug("Attempt to delete  user with ID: {} with hard delete", userId);

    var user = findUserByUserId(userId);
    validateSelfModification(user, "hard delete");
    userRepository.delete(user);

    try {
      userManagementApi.deleteUser(new DeleteUserRequest(user.getUserId()));
      stripeService.deleteUser(user);
    } catch (CustomFeignException e) {
      log.debug(
          "Error occurred during call to the user management service. Details: {}", e.getMessage());
      throw new InternalAuthServiceException(
          "Error occurred while deleting. Please try again later");
    } catch (StripeException e) {
      log.error(
          "Stripe API call failed: {}. Error code: {}. StackTrace: ",
          e.getMessage(),
          e.getCode(),
          e);
      throw new InternalAuthServiceException(
          String.format(
              "Error occurred while deleting stripe customer account with user ID: %s and Stripe customer ID: %s",
              user.getUserId(), user.getStripeCustomerId()));
    }

    log.debug("User user with ID: {} deleted successfully with hard delete", userId);

    // todo how to rollback changes when request two different services through rest?
  }

  /**
   * Validates if the provided action is being performed on the user itself.
   *
   * <p>This method checks if the user performing the action is trying to modify their own account.
   * If so, a {@link ForbiddenActionException} is thrown with an appropriate message indicating that
   * performing the action on oneself is not allowed.
   *
   * @param user The user being modified or acted upon.
   * @param action The action being performed, such as "block", "unblock", etc. Used for error
   *     message.
   * @throws ForbiddenActionException If the user is trying to modify their own account.
   */
  private void validateSelfModification(User user, String action) {
    boolean isSelfModification = user.getUserId().equals(authService.getUserIdFromAuthentication());
    if (isSelfModification) {
      log.debug("Attempt to {} oneself. User with ID: {}", action, user.getUserId());
      throw new ForbiddenActionException(String.format("You can't %s yourself", action));
    }
  }

  /**
   * Validates if the current user has permission to modify a super admin.
   *
   * <p>Ensures that only a super admin can edit another super admin. If a regular admin attempts to
   * modify a super admin, an exception is thrown.
   *
   * @param user The user being modified.
   * @throws ForbiddenActionException if a non-super admin attempts to modify a super admin.
   */
  private void validateSuperAdminModificationPermission(User user) {
    boolean isEditedUserSuperAdmin =
        user.getRoles().stream().anyMatch(role -> role.getName().equals(UserRole.ROLE_SUPER_ADMIN));

    if (isEditedUserSuperAdmin) {
      boolean isEditorSuperAdmin =
          authService.getUserRolesFromAuthentication().stream()
              .anyMatch(role -> role.equals(UserRole.ROLE_SUPER_ADMIN.name()));
      if (!isEditorSuperAdmin) {
        log.debug("Attempt to block user with ROLE_SUPER_ADMIN with ROLE_ADMIN");
        throw new ForbiddenActionException("You can't edit user with ROLE_SUPER_ADMIN");
      }
    }
  }

  /**
   * Finds a user by their unique identifier (userId).
   *
   * @param userId the unique identifier of the user
   * @return the {@link User} corresponding to the provided ID
   * @throws EntityNotFoundException if a user with the specified ID is not found
   */
  private User findUserByUserId(String userId) {
    return userRepository
        .findByUserId(userId)
        .orElseThrow(
            () -> {
              log.debug("User with ID: {} not found", userId);
              return new EntityNotFoundException(
                  String.format("User with ID: %s not found", userId));
            });
  }

  /**
   * Finds a user by their email address.
   *
   * @param email the email address of the user
   * @return the User corresponding to the provided email
   * @throws EntityNotFoundException if a user with the specified email is not found
   */
  private User findUserByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(
            () -> {
              log.debug("User with email: {} not found", email);
              return new EntityNotFoundException(
                  String.format("User with email: %s not found", email));
            });
  }
}
