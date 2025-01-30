package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.InvalidRequestException;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.model.UserDetailsImpl;
import com.ifortex.internship.authservice.model.mapper.UserMapper;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.UserService;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthService authService;
  private final UserMapper userMapper;

  public User findUserByUserId(String userId) {
    return userRepository
        .findByUserId(userId)
        .orElseThrow(
            () -> {
              log.debug("User with ID: {} not found", userId);
              return new EntityNotFoundException(
                  String.format("User with email: %s not found", userId));
            });
  }

  public User findUserByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(
            () -> {
              log.debug("User with email: {} not found", email);
              return new EntityNotFoundException(
                  String.format("User with email: %s not found", email));
            });
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

  public AuthUserDto getUserDtoByUserId(String userId) {
    log.debug("Getting user by userId: {}", userId);

    var user = findUserByUserId(userId);
    return userMapper.toDto(user);
  }

  public List<AuthUserDto> getAllUsers() {

    log.debug("Getting all users");

    List<User> users = userRepository.findAll();
    List<AuthUserDto> authUserDtoList = users.stream().map(userMapper::toDto).toList();

    return authUserDtoList;
  }

  public AuthUserDto getUserByAuthentication() {

    Object principle = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    UserDetailsImpl userDetails;
    AuthUserDto authUserDto;

    if ("anonymousUser".equals(principle.toString())) {
      log.debug("Attempt to get user details by anonymous or unauthenticated user.");
      throw new AuthorizationException("User is not authenticated. Please log in.");
    }
    userDetails = (UserDetailsImpl) principle;
    authUserDto = getUserDtoByUserId(userDetails.getUserId());
    return authUserDto;
  }
}
