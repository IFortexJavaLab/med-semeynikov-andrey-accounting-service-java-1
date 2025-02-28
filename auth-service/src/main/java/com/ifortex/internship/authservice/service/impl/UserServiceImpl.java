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
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.service.RedisService;
import com.ifortex.internship.authservice.stripe.service.StripeService;
import com.ifortex.internship.authservice.util.UserMapper;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.BlockUserRequest;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.request.UnblockUserRequest;
import com.ifortex.internship.authserviceapi.dto.request.UpdateUserDto;
import com.ifortex.internship.authserviceapi.dto.request.UserSearchRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.ChangeEmailResponse;
import com.ifortex.internship.authserviceapi.dto.response.ClientDto;
import com.ifortex.internship.authserviceapi.dto.response.UserListViewDto;
import com.stripe.exception.StripeException;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl {

    private final StripeService stripeService;

    @Value("${app.otp.emailExpirationMinutes}")
    private int expirationMinutes;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthServiceImpl authService;
    private final RedisService redisService;
    private final EmailService emailService;
    private final Environment environment;
    private final CustomAuthenticationProvider authenticationProvider;
    private final UserMapper userMapper;

    public AuthUserDto getUserByUserId(String userId) {
        log.debug("Getting user by userId: {}", userId);

        var user = findUserByUserId(userId);
        return userMapper.userToAuthUserDto(user);
    }

    public AuthUserDto getUser() {
        String userId = authService.getUserIdFromAuthentication();
        return getUserByUserId(userId);
    }

    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request, String userEmail) {

        log.debug("Changing password for user with email: {}", userEmail);

        User user = findUserByEmail(userEmail);

        try {
            authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken(userEmail, request.getCurrentPassword()));
        } catch (AuthorizationException ex) {
            throw new InvalidRequestException("Current password is incorrect");
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            log.debug(
                "Current password and new password are equal for user with email: {}", user.getEmail());
            throw new InvalidRequestException("Current password and new password are equal");
        }

        String newEncodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(newEncodedPassword);
        user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
        user.setTemporaryPassword(null);
        userRepository.save(user);

        log.info("User with email: {} successfully changed password", userEmail);

        String link = "http://localhost:8085/api/v1/auth/login";

        String message = "Changed password successfully, please log in again using this link: ";

        AuthResponse authResponse = authService.logoutUser();
        authResponse.setMessage(message);
        authResponse.setLink(link);

        return authResponse;
    }

    @Transactional
    public ChangeEmailResponse changeEmailRequest(String newEmail) {

        var currentEmail = authService.getUserEmailFromAuthentication();
        var userId = authService.getUserIdFromAuthentication();

        if (currentEmail.equals(newEmail)) {
            log.error("New provided email: {} is equal to the current one for user with ID: {}", newEmail, userId);
            throw new InvalidRequestException("New provided email is equal to the current one");
        }

        if (userRepository.findByEmail(newEmail).isPresent()) {
            log.error("User with email: {} already registered in the system", newEmail);
            throw new InvalidRequestException("New email already registered in the system");
        }

        log.info("Processing request for changing email for user: {}", currentEmail);

        String otp = authService.generateOtp();
        log.debug("Otp for user with ID: {} generated successfully", userId);

        String redisKey = RedisKeyPrefix.EMAIL_CHANGE.getPrefix() + newEmail;
        redisService.saveOtp(redisKey, otp, expirationMinutes);
        log.debug("Otp saved to db successfully for user with ID: {}", userId);

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
                newEmail);

        log.info("An email with an otp has been sent to email: {} for user with ID: {}", newEmail, userId);
        return new ChangeEmailResponse(message, changeEmailLink, expirationMinutes);
    }

    @Transactional
    public ChangeEmailResponse changeEmailConfirm(String newEmail, String code) {

        var currentEmail = authService.getUserEmailFromAuthentication();
        var userId = authService.getUserIdFromAuthentication();

        log.info("Changing email for user with ID: {}", userId);
        String redisKey = RedisKeyPrefix.EMAIL_CHANGE.getPrefix() + newEmail;
        String storedOtp = redisService.getOtp(redisKey);

        if (!code.equals(storedOtp)) {
            log.error("OTP has expired or is invalid for email: {} for user with ID: {}", newEmail, userId);
            throw new InvalidRequestException("OTP has expired or is invalid. Please try again.");
        }

        var user =
            userRepository
                .findByEmail(currentEmail)
                .orElseThrow(
                    () -> {
                        log.error("User with email: {} not found", currentEmail);
                        return new InvalidRequestException("Failed to verify otp. Please try again.");
                    });

        user.setEmail(newEmail);
        user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
        user.setRefreshToken(null);

        userRepository.save(user);
        redisService.deleteOtp(redisKey);

        String loginLink = environment.getProperty("app.link.login");
        String message = "Changed email successfully, please log in again using this link:";

        log.info("Email was changed successfully for user with ID {}", userId);
        return new ChangeEmailResponse(message, loginLink);
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

        log.debug("User user with ID: {} deleted successfully with soft delete", userId);
    }

    @Transactional
    public void hardDelete(String userId) {

        log.debug("Attempt to delete  user with ID: {} with hard delete", userId);

        var user = findUserByUserId(userId);
        validateSelfModification(user, "hard delete");
        userRepository.delete(user);

        try {
            stripeService.deleteUser(user);
        } catch (StripeException e) {
            log.error(
                "Stripe API call failed: {}. Error code: {}. StackTrace: ",
                e.getMessage(), e.getCode(), e);
            throw new InternalAuthServiceException(
                String.format(
                    "Error occurred while deleting stripe customer account with user ID: %s and Stripe customer ID: %s",
                    user.getUserId(), user.getStripeCustomerId()));
        }

        log.debug("User user with ID: {} deleted successfully with hard delete", userId);
    }

    /**
     * Validates if the provided action is being performed on the user itself.
     *
     * <p>This method checks if the user performing the action is trying to modify their own account.
     * If so, a {@link ForbiddenActionException} is thrown with an appropriate message indicating that performing the action on oneself is not
     * allowed.
     * @param user The user being modified or acted upon.
     * @param action The action being performed, such as "block", "unblock", etc. Used for error message.
     * @throws ForbiddenActionException If the user is trying to modify their own account.
     */
    private void validateSelfModification(User user, String action) {
        boolean isSelfModification = user.getUserId().equals(authService.getUserIdFromAuthentication());
        if (isSelfModification) {
            log.debug("Attempt to {} oneself. User with ID: {}", action, user.getUserId());
            throw new ForbiddenActionException(String.format("You can't %s yourself", action));
        }
    }

    //todo refactor this method after entity audit
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

    public ClientDto getUserProfileByAuthentication() {

        String userId = authService.getUserIdFromAuthentication();
        log.info("Getting user profile for user with ID: {}", userId);

        var user = findUserByUserId(userId);
        var clientDto = userMapper.userToClientDto(user);

        log.info("Successfully fetched user profile for ID: {}", userId);

        return clientDto;
    }

    @Transactional
    public ClientDto updateUserByAuthentication(UpdateUserDto updateUserDto) {

        String userId = authService.getUserIdFromAuthentication();
        log.info("Updating user with ID: {}", userId);

        var user = findUserByUserId(userId);
        userMapper.updateUserFromDto(updateUserDto, user);

        user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
        userRepository.save(user);
        log.debug("User with ID: {} successfully updated in database", userId);

        log.info("Update process completed for user with ID: {}", userId);

        return userMapper.userToClientDto(user);
    }

    @Transactional
    public ClientDto updateUserByAdmin(String userId, UpdateUserDto updateUserDto) {

        var adminId = authService.getUserIdFromAuthentication();

        log.debug("Updating user with ID: {} by admin with ID: {}", userId, adminId);
        User user = findUserByUserId(userId);

        //todo add check permission to edit target user

        userMapper.updateUserFromDto(updateUserDto, user);

        user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
        user = userRepository.save(user);
        log.debug("User with ID: {} saved to db", userId);

        log.info("User with ID: {} updated successfully", userId);

        return userMapper.userToClientDto(user);
    }

    public ClientDto getUserProfileById(String userId) {

        var adminId = authService.getUserIdFromAuthentication();
        log.info("Getting user profile for user with ID: {} by Admin with ID: {}", userId, adminId);

        var user = findUserByUserId(userId);
        log.info("Successfully retrieved user profile for user with ID: {}", userId);
        return userMapper.userToClientDto(user);
    }

    public Page<UserListViewDto> searchUsers(UserSearchRequest request, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        log.info("Searching users with filters: firstName={}, lastName={}, phone={}, email={}, roles={}, status={}, page={}, size={}",
            request.getFirstName(), request.getLastName(), request.getPhone(),
            request.getEmail(), request.getRoles(), request.getStatus(), page, size);

        Page<User> userManagementUsers =
            userRepository.findByFilters(
                request.getFirstName(),
                request.getLastName(),
                request.getPhone(),
                request.getEmail(),
                request.getRoles(),
                request.getStatus(),
                pageable);

        Page<UserListViewDto> userListViewDto = userManagementUsers.map(userMapper::userToUserListViewDto);

        log.info("Found {} users (total pages: {})",
            userManagementUsers.getTotalElements(),
            userManagementUsers.getTotalPages());

        return userListViewDto;
    }
}
