package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authservice.dto.request.PasswordResetWithOtpDto;
import com.ifortex.internship.authservice.dto.request.SocialUserInfo;
import com.ifortex.internship.authservice.dto.request.UpdateAccountDto;
import com.ifortex.internship.authservice.dto.response.AuthResponse;
import com.ifortex.internship.authservice.dto.response.ChangeEmailResponse;
import com.ifortex.internship.authservice.dto.response.CreatedAccountDto;
import com.ifortex.internship.authservice.dto.response.SuccessResponse;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.Role;
import com.ifortex.internship.authservice.model.TemporaryPassword;
import com.ifortex.internship.authservice.model.constant.RedisKeyPrefix;
import com.ifortex.internship.authservice.repository.AccountRepository;
import com.ifortex.internship.authservice.util.PasswordGenerator;
import com.ifortex.internship.authservice.util.UserMapper;
import com.ifortex.internship.authserviceapi.dto.response.AccountDto;
import com.ifortex.internship.medstarter.exception.custom.AuthorizationException;
import com.ifortex.internship.medstarter.exception.custom.DuplicateResourceException;
import com.ifortex.internship.medstarter.exception.custom.EmailSendException;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import com.ifortex.internship.medstarter.exception.custom.InvalidRequestException;
import com.ifortex.internship.medstarter.model.constant.LinkConstants;
import com.ifortex.internship.medstarter.security.service.AuthenticationFacade;
import jakarta.annotation.Nullable;
import jakarta.mail.MessagingException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountService {

    static final String LOG_ACCOUNT_NOT_FOUND = "User with email: {} not found";
    static final String LOG_SENDING_EMAIL_ERROR = "Error during sending verification email for: {}. There details: {}";

    static final String PASSWORD_RESET = "Password reset";
    static final String EMAIL_CHANGE = "Email change";

    AccountRepository accountRepository;
    PasswordEncoder passwordEncoder;
    AuthService authService;
    RedisService redisService;
    UserMapper userMapper;
    PasswordGenerator passwordGenerator;
    UserNotificationService userNotificationService;
    AuthenticationFacade authenticationFacade;

    @Value("${app.otp.emailExpirationMinutes}") int expirationMinutes;
    @Value("${app.otp.resetPasswordExpirationMinutes}") int resetPasswordExpirationMinutes;
    @Value("${app.tempPassword.expirationHours}") int tempPasswordExpirationHours;

    @Transactional
    public CreatedAccountDto createAccount(String email, String password, Role role, @Nullable String firstName) {
        log.debug("Creating account with email: {}", email);

        Account account =
            new Account()
                .setAccountId(UUID.randomUUID())
                .setEmail(email)
                .setRole(role);

        if (firstName != null) {
            account.setFirstName(firstName);
        }

        String hashedPassword;
        if (password == null) {

            password = passwordGenerator.generateTempPassword();
            hashedPassword = passwordEncoder.encode(password);
            TemporaryPassword temporaryPassword =
                new TemporaryPassword(account, hashedPassword,
                    Instant.now().plusSeconds(TimeUnit.HOURS.toSeconds(tempPasswordExpirationHours)));
            account.setTemporaryPassword(temporaryPassword);

        } else {
            hashedPassword = passwordEncoder.encode(password);
            account.setPasswordHash(hashedPassword);
        }
        accountRepository.save(account);

        return new CreatedAccountDto(account, password, tempPasswordExpirationHours);
    }

    @Transactional
    public Account createAccountForSocialClient(SocialUserInfo socialUserInfo, Role role) {
        log.info("Creating account for social user with email: {}", socialUserInfo.getEmail());

        Account account =
            new Account()
                .setAccountId(UUID.randomUUID())
                .setEmail(socialUserInfo.getEmail())
                .setRole(role)
                .setFirstName(socialUserInfo.getFirstName())
                .setLastName(socialUserInfo.getLastName())
                .setProvider(socialUserInfo.getProvider())
                .setTwoFactorEnabled(false);

        accountRepository.save(account);
        log.info("Account:{} for social user created successfully", account.getAccountId());

        return account;
    }

    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request, String userEmail) {
        log.debug("Changing password for user with email: {}", userEmail);

        Account account = findAccountByEmail(userEmail);

        try {
            authService.createAuthentication(userEmail, request.getCurrentPassword());
        } catch (AuthorizationException ex) {
            throw new InvalidRequestException("Current password is incorrect");
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            log.debug(
                "Current password and new password are equal for user with email: {}", account.getEmail());
            throw new InvalidRequestException("Current password and new password are equal");
        }

        String newEncodedPassword = passwordEncoder.encode(request.getNewPassword());
        account.setPasswordHash(newEncodedPassword);
        account.setTemporaryPassword(null);
        accountRepository.save(account);

        log.info("Account with email: {} successfully changed password", userEmail);

        String message = "Changed password successfully, please log in again using this link: ";

        AuthResponse authResponse = authService.logoutUser();
        authResponse.setMessage(message);
        authResponse.setLink(LinkConstants.LOGIN);

        return authResponse;
    }

    @Transactional
    public ChangeEmailResponse changeEmailRequest(String newEmail) {
        var currentEmail = authenticationFacade.getUserEmailFromAuthentication();
        var userId = authenticationFacade.getAccountIdFromAuthentication();

        if (currentEmail.equals(newEmail)) {
            log.error("New provided email: {} is equal to the current one for user with ID: {}", newEmail, userId);
            throw new InvalidRequestException("New provided email is equal to the current one");
        }

        if (accountRepository.findByEmail(newEmail).isPresent()) {
            log.error("User with email: {} already registered in the system", newEmail);
            throw new InvalidRequestException("New email already registered in the system");
        }

        log.info("Processing request for changing email for user: {}", currentEmail);

        String otp = passwordGenerator.generateOtp();
        log.debug("Otp for user with ID: {} generated successfully", userId);

        String redisKey = RedisKeyPrefix.EMAIL_CHANGE.getPrefix() + newEmail;
        redisService.saveOtp(redisKey, otp, expirationMinutes);
        log.debug("Otp saved to db successfully for user with ID: {}", userId);

        try {
            userNotificationService.sendVerificationEmail(newEmail, EMAIL_CHANGE, otp);
        } catch (MessagingException e) {
            log.error(LOG_SENDING_EMAIL_ERROR, currentEmail, e.getMessage());
            throw new EmailSendException("Failed to send verification email");
        }

        String message =
            String.format(
                "An email with a change email code has been sent to your email: %s, please follow this link:",
                newEmail);

        log.info("An email with an otp has been sent to email: {} for user with ID: {}", newEmail, userId);
        return new ChangeEmailResponse(message, LinkConstants.CHANGE_EMAIL, expirationMinutes);
    }

    @Transactional
    public ChangeEmailResponse changeEmailConfirm(String newEmail, String code) {
        var currentEmail = authenticationFacade.getUserEmailFromAuthentication();
        var userId = authenticationFacade.getAccountIdFromAuthentication();

        log.info("Changing email for user with ID: {}", userId);
        String redisKey = RedisKeyPrefix.EMAIL_CHANGE.getPrefix() + newEmail;
        String storedOtp = redisService.getOtp(redisKey);

        if (!code.equals(storedOtp)) {
            log.error("OTP has expired or is invalid for email: {} for user with ID: {}", newEmail, userId);
            throw new InvalidRequestException("OTP has expired or is invalid. Please try again.");
        }

        var account =
            accountRepository
                .findByEmail(currentEmail)
                .orElseThrow(
                    () -> {
                        log.error(LOG_ACCOUNT_NOT_FOUND, currentEmail);
                        return new InvalidRequestException("Failed to verify otp. Please try again.");
                    });

        account.setEmail(newEmail);
        account.setRefreshToken(null);

        accountRepository.save(account);
        redisService.deleteOtp(redisKey);

        String message = "Changed email successfully, please log in again using this link:";

        log.info("Email was changed successfully for user with ID {}", userId);
        return new ChangeEmailResponse(message, LinkConstants.LOGIN);
    }

    public SuccessResponse passwordResetRequest(String email) {
        log.debug("Initiating password reset for email: {}", email);

        if (accountRepository.findByEmail(email).isEmpty()) {
            log.debug(LOG_ACCOUNT_NOT_FOUND, email);
            throw new EntityNotFoundException(String.format("User with email: %s not found", email));
        }

        String otp = passwordGenerator.generateOtp();
        log.debug("Otp for user: {} generated successfully", email);

        String redisKey = RedisKeyPrefix.PASSWORD_RESET.getPrefix() + email;
        redisService.saveOtp(redisKey, otp, resetPasswordExpirationMinutes);
        log.debug("Otp saved to db successfully for user: {}", email);

        try {
            userNotificationService.sendVerificationEmail(email, PASSWORD_RESET, otp);
        } catch (MessagingException e) {
            log.error(LOG_SENDING_EMAIL_ERROR, email, e.getMessage());
            throw new EmailSendException(String.format("Failed to send verification email to the: %s", email));
        }

        String message = String.format("An email with a password reset code has been sent to your email: %s, please follow this link: ", email);
        return new SuccessResponse(message, LinkConstants.RESET_PASSWORD_CONFIRM);
    }

    @Transactional
    public SuccessResponse passwordResetConfirm(PasswordResetWithOtpDto request) {
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

        var account = accountRepository.findByEmail(userEmail).orElseThrow(() -> {
            log.debug(LOG_ACCOUNT_NOT_FOUND, userEmail);
            return new AuthorizationException("Failed to reset password. Please try again.");
        });

        String newEncodedPassword = passwordEncoder.encode(request.getNewPassword());
        account.setPasswordHash(newEncodedPassword);
        account.setTemporaryPassword(null);
        accountRepository.save(account);

        redisService.deleteOtp(redisKey);

        log.info("User with email: {} successfully changed password", userEmail);

        String message = "Changed password successfully, please log in again using this link: ";
        return new SuccessResponse(message, LinkConstants.LOGIN);
    }

    public AccountDto getUserProfileByAuthentication() {

        UUID userId = authenticationFacade.getAccountIdFromAuthentication();
        log.info("Getting user profile for user with ID: {}", userId);

        var account = findAccountByAccountId(userId);
        var clientDto = userMapper.userToClientDto(account);

        log.info("Successfully fetched user profile for ID: {}", userId);

        return clientDto;
    }

    @Transactional
    public AccountDto updateUserByAuthentication(UpdateAccountDto updateAccountDto) {

        UUID accountId = authenticationFacade.getAccountIdFromAuthentication();
        log.info("Updating account with ID: {}", accountId);

        var account = findAccountByAccountId(accountId);
        userMapper.updateAccountFromDto(updateAccountDto, account);

        accountRepository.save(account);
        log.debug("Account with ID: {} successfully updated in database", accountId);

        log.info("Update process completed for account with ID: {}", accountId);

        return userMapper.userToClientDto(account);
    }

    public void validateEmailNotRegistered(String email) {
        if (accountRepository.findByEmail(email).isPresent()) {
            log.error("Email: {} is already registered", email);
            throw new DuplicateResourceException(String.format("Email: %s is already registered.", email));
        }
    }

    public Account findAccountByAccountId(UUID accountId) {
        return accountRepository
            .findByAccountId(accountId)
            .orElseThrow(
                () -> {
                    log.error("Account with ID: {} not found", accountId);
                    return new EntityNotFoundException(
                        String.format("Account with ID: %s not found", accountId));
                });
    }

    private Account findAccountByEmail(String email) {
        return accountRepository
            .findByEmail(email)
            .orElseThrow(
                () -> {
                    log.debug("Account with email: {} not found", email);
                    return new EntityNotFoundException(
                        String.format("Account with email: %s not found", email));
                });
    }
}
