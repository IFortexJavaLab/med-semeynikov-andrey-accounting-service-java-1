package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.email.EmailService;
import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EmailSendException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.InvalidRequestException;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.model.constant.RedisKeyPrefix;
import com.ifortex.internship.authservice.model.mapper.UserMapper;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.RedisService;
import com.ifortex.internship.authservice.service.UserService;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.request.TwoFactorAuthRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.ChangeEmailResponse;
import jakarta.mail.MessagingException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  @Value("${app.otp.emailExpirationMinutes}")
  private int expirationMinutes;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthService authService;
  private final UserMapper userMapper;
  private final RedisService redisService;
  private final EmailService emailService;
  private final Environment environment;

  public AuthUserDto getUserByUserId(String userId) {
    log.debug("Getting user by userId: {}", userId);

    var user = findUserByUserId(userId);
    return userMapper.toDto(user);
  }

  public AuthUserDto getUser() {
    String userId = authService.getUserIdFromAuthentication();
    AuthUserDto authUserDto = getUserByUserId(userId);
    return authUserDto;
  }

  // todo implement with pagination
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

    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
      log.info("Incorrect password for user with email: {}", user.getEmail());
      throw new AuthorizationException(
          String.format("Incorrect password for user with email: %s", user.getEmail()));
    }

    if (request.getCurrentPassword().equals(request.getPasswordConfirmation())) {
      log.info(
          "Current password and new password are equal for user with email: {}", user.getEmail());
      throw new InvalidRequestException(
          String.format(
              "Current password and new password are equal for user with email: %s",
              user.getEmail()));
    }

    boolean passwordMismatch = !request.getNewPassword().equals(request.getPasswordConfirmation());
    if (passwordMismatch) {
      log.info(
          "Password and password confirmation  do not match for user with email: {}",
          user.getEmail());
      throw new InvalidRequestException("Password and confirmation password do not match.");
    }

    String newEncodedPassword = passwordEncoder.encode(request.getNewPassword());
    user.setPassword(newEncodedPassword);
    user.setUpdatedAt(LocalDateTime.now());
    userRepository.save(user);

    log.info("User with email: {} successfully changed password", userEmail);

    String link = "http://localhost:8085/api/v1/auth/login";

    String message =
        String.format(
            "Changed password successfully for user with email %s, please log in again using this link: %s",
            user.getEmail(), link);

    AuthResponse authResponse = authService.logoutUser();
    authResponse.setMessage(message);

    return authResponse;
  }

  @Transactional
  public ChangeEmailResponse changeEmail(String newEmail, String code) {

    String currentEmail = authService.getUserEmailFromAuthentication();

    if (currentEmail.equals(newEmail)) {
      log.debug("New provided email is equal to the current one for user: {}", currentEmail);
      throw new InvalidRequestException("New provided email is equal to the current one");
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
              "An email with a change email code has been sent to your email: %s, please follow this link: %s",
              currentEmail, changeEmailLink);
      return new ChangeEmailResponse(message);
    }

    String redisKey = RedisKeyPrefix.EMAIL_CHANGE.getPrefix() + currentEmail;
    String storedOtp = redisService.getOtp(redisKey);

    if (!code.equals(storedOtp)) {
      log.debug("OTP has expired or is invalid for email: {}", currentEmail);
      log.info("Failed to change email for user: {}", currentEmail);
      throw new AuthorizationException("OTP has expired or is invalid. Please try again.");
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
    userRepository.save(user);
    redisService.deleteOtp(redisKey);

    AuthUserDto authUserDto = userMapper.toDto(user);
    String loginLink = environment.getProperty("app.link.login");
    String message =
        String.format(
            "Changed email successfully, please log in again using this link: %s", loginLink);

    return new ChangeEmailResponse(authUserDto, message);
  }

  @Transactional
  public AuthUserDto changeTwoFactorAuth(TwoFactorAuthRequest request) {

    String email = authService.getUserEmailFromAuthentication();

    log.debug("Changing 2FA for user: {}", email);

    User userFromDb = findUserByEmail(email);

    Boolean newTwoFactorState = request.getIsTwoFactorEnabled();
    if (newTwoFactorState == null) {
      return userMapper.toDto(userFromDb);
    }

    if (newTwoFactorState == userFromDb.isTwoFactorEnabled()) {
      log.debug("2FA state is the same for user: {}", email);
      return userMapper.toDto(userFromDb);
    }

    log.info("Updating 2FA state for user: {}", email);
    userFromDb.setTwoFactorEnabled(newTwoFactorState);
    userRepository.save(userFromDb);

    return userMapper.toDto(userFromDb);
  }

  @Transactional
  public AuthUserDto changeTwoFactorAuthByAdmin(String userId, TwoFactorAuthRequest request) {
    log.debug("Changing 2FA for user with id: {} by admin", userId);

    User userFromDb = findUserByUserId(userId);

    Boolean newTwoFactorState = request.getIsTwoFactorEnabled();
    if (newTwoFactorState == null) {
      return userMapper.toDto(userFromDb);
    }

    if (newTwoFactorState == userFromDb.isTwoFactorEnabled()) {
      log.debug("2FA state is the same for user with id: {}", userId);
      return userMapper.toDto(userFromDb);
    }

    log.info("Updating 2FA state for user with id: {}", userId);
    userFromDb.setTwoFactorEnabled(newTwoFactorState);
    userRepository.save(userFromDb);

    return userMapper.toDto(userFromDb);
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
                  String.format("User with email: %s not found", userId));
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
