package com.ifortex.internship.accountingservice.service;

import com.ifortex.internship.accountingservice.dto.request.LoginRequest;
import com.ifortex.internship.accountingservice.dto.request.VerifyLoginOtpRequest;
import com.ifortex.internship.accountingservice.dto.response.AuthResponse;
import com.ifortex.internship.accountingservice.dto.response.SuccessResponse;
import com.ifortex.internship.accountingservice.dto.response.TemporaryPasswordResponse;
import com.ifortex.internship.accountingservice.dto.response.TokensResponse;
import com.ifortex.internship.accountingservice.model.Account;
import com.ifortex.internship.accountingservice.model.RefreshToken;
import com.ifortex.internship.accountingservice.model.TemporaryPassword;
import com.ifortex.internship.accountingservice.model.constant.RedisKeyPrefix;
import com.ifortex.internship.accountingservice.repository.AccountRepository;
import com.ifortex.internship.accountingservice.repository.RefreshTokenRepository;
import com.ifortex.internship.accountingservice.repository.TemporaryPasswordRepository;
import com.ifortex.internship.accountingservice.util.PasswordGenerator;
import com.ifortex.internship.medstarter.exception.custom.AuthorizationException;
import com.ifortex.internship.medstarter.exception.custom.EmailSendException;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import com.ifortex.internship.medstarter.exception.custom.ForbiddenActionException;
import com.ifortex.internship.medstarter.exception.custom.InvalidRequestException;
import com.ifortex.internship.medstarter.model.constant.LinkConstants;
import com.ifortex.internship.medstarter.security.dto.AdminDetailsDto;
import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import com.ifortex.internship.medstarter.security.service.AuthenticationFacade;
import jakarta.mail.MessagingException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    static final String LOG_ACCOUNT_NOT_FOUND_EMAIL = "Account with email: {} not found";
    static final String LOG_ACCOUNT_NOT_FOUND_ID = "Account with ID: {} not found";
    static final String LOG_REFRESH_TOKEN_DELETED = "Refresh token deleted successfully for user: {}";
    static final String LOG_SENDING_EMAIL_ERROR = "Error during sending 2FA verification email for: {}. StackTrace: {}";
    static final String LOG_PASSWORD_HAS_BEEN_RESET = "Password has been reset for user: {} by admin: {}";

    static final String PASSWORD_RESET = "Password reset";
    static final String VERIFICATION_CODE_2FA = "2FA Verification Code";

    JwtTokenIssuer jwtTokenIssuer;
    RedisService redisService;
    PasswordEncoder passwordEncoder;
    AccountRepository accountRepository;
    PasswordGenerator passwordGenerator;
    RefreshTokenRepository refreshTokenRepository;
    TemporaryPasswordRepository passwordRepository;
    CustomAuthenticationProvider authenticationProvider;
    AuthenticationFacade authenticationFacade;
    UserNotificationService userNotificationService;

    @Value("${app.jwtExpirationS}") Long jwtExpirationS;
    @Value("${app.otp.loginExpirationMinutes}") int loginOtpExpirationMinutes;
    @Value("${app.tempPassword.expirationHours}") int tempPasswordExpirationHours;
    @Value("${app.refreshTokenExpirationS}") Long refreshTokenExpirationS;

    public AuthResponse authenticateUser(LoginRequest loginRequest) {
        String accountEmail = loginRequest.getEmail();
        log.debug("Authenticating user with email: {}", accountEmail);

        Authentication authentication = createAuthentication(accountEmail, loginRequest.getPassword());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        Account account = (Account) authentication.getPrincipal();

        log.debug("User with account: {} successfully authenticated.", account.getAccountId());

        if (account.isTwoFactorEnabled()) {
            return authenticateWith2FA(account);
        }

        return buildAuthResponse(account);
    }

    @Transactional
    public AuthResponse authenticateSocialUser(Account account) {
        UUID accountId = account.getAccountId();
        log.debug("Authenticating social user with account: {}", accountId);
        return buildAuthResponse(account);
    }

    @Transactional
    public AuthResponse completeLoginWithOtp(VerifyLoginOtpRequest request) {
        String accountEmail = request.getEmail();
        log.debug("Verifying otp to log in for email: {}", accountEmail);

        String otpFromRequest = request.getOtp();
        String redisKey = RedisKeyPrefix.LOGIN_OTP.getPrefix() + accountEmail;
        String storedOtp = redisService.getOtp(redisKey);

        if (!otpFromRequest.equals(storedOtp)) {
            log.debug("OTP has expired or is invalid for email: {}", accountEmail);
            log.info("Failed to login for user: {}", accountEmail);
            throw new InvalidRequestException("OTP has expired or is invalid. Please try again.");
        }

        redisService.deleteOtp(redisKey);

        var account = accountRepository.findByEmail(accountEmail).orElseThrow(() -> {
            log.debug(LOG_ACCOUNT_NOT_FOUND_EMAIL, accountEmail);
            return new AuthorizationException("Failed to verify otp. Please try again.");
        });

        return buildAuthResponse(account);
    }

    @Transactional
    public AuthResponse logoutUser() {
        String userEmail = authenticationFacade.getUserEmailFromAuthentication();
        log.info("Logout attempt for user: {}", userEmail);
        refreshTokenRepository.deleteRefreshTokenByAccountEmail(userEmail);
        log.debug(LOG_REFRESH_TOKEN_DELETED, userEmail);

        log.info("Logout successful for user: {}", userEmail);
        return AuthResponse.builder().message(String.format("Logout successful for user %s", userEmail)).build();
    }

    @Transactional
    public TemporaryPasswordResponse resetPasswordWithTemp(UUID accountId) {
        log.debug("Initiating password reset for user with account ID: {}", accountId);
        AdminDetailsDto adminDetails = authenticationFacade.getAdminDetailsFromAuthentication();

        Account account = accountRepository.findByAccountId(accountId).orElseThrow(() -> {
            log.error(LOG_ACCOUNT_NOT_FOUND_ID, accountId);
            return new EntityNotFoundException(String.format("Account with ID: %s not found", accountId));
        });

        validateUserModificationPermission(adminDetails, account);
        deleteOldTemporaryPassword(account);

        String tempPassword = passwordGenerator.generateTempPassword();
        String hashedPassword = passwordEncoder.encode(tempPassword);
        var temporaryPassword =
            new TemporaryPassword(
                account,
                hashedPassword,
                Instant.now().plusSeconds(TimeUnit.HOURS.toSeconds(tempPasswordExpirationHours)));

        account.setPasswordHash(null);
        account.setTemporaryPassword(temporaryPassword);
        accountRepository.save(account);
        log.debug("Temporary password created and saved for account ID: {}", accountId);

        refreshTokenRepository.deleteRefreshTokenByAccountEmail(account.getEmail());
        log.debug(LOG_REFRESH_TOKEN_DELETED, account.getEmail());

        log.info(LOG_PASSWORD_HAS_BEEN_RESET, accountId, adminDetails.getAccountId());

        return new TemporaryPasswordResponse(tempPassword, tempPasswordExpirationHours);
    }

    public void validateUserModificationPermission(AdminDetailsDto changer, Account targetAccount) {

        UUID editorAccountId = changer.getAccountId();
        UUID targetAccountId = targetAccount.getAccountId();
        UserRole targetRole = targetAccount.getRole().getName();

        log.debug("Validating user modification permission. Editor ID: {}, Target ID: {}, Target Role: {}",
            editorAccountId, targetAccountId, targetRole);

        if (changer.isSuperAdmin()) {
            log.debug("Editor with ID: {} is a super admin. Modification allowed.", editorAccountId);
            return;
        }

        boolean
            isTargetUserClientOrParamedic =
            targetAccount.getRole().getName().equals(UserRole.CLIENT) ||
            targetAccount.getRole().getName().equals(UserRole.PARAMEDIC);

        if (!isTargetUserClientOrParamedic) {
            log.error("Permission denied. Editor ID: {}, Target ID: {}, Target Role: {}",
                editorAccountId, targetAccountId, targetRole);
            throw new ForbiddenActionException("You can't edit this user");
        }
        log.debug("User modification validation passed. Editor ID: {}, Target ID: {}", editorAccountId, targetAccountId);
    }

    @Transactional
    public SuccessResponse resetPasswordWithEmail(UUID accountId) {
        log.debug("Initiating password reset request email for user with ID: {}", accountId);

        AdminDetailsDto adminDetails = authenticationFacade.getAdminDetailsFromAuthentication();

        Account account = accountRepository.findByAccountId(accountId).orElseThrow(() -> {
            log.error(LOG_ACCOUNT_NOT_FOUND_ID, accountId);
            return new EntityNotFoundException(String.format("Account with ID: %s not found", accountId));
        });
        String accountEmail = account.getEmail();

        validateUserModificationPermission(adminDetails, account);

        deleteOldTemporaryPassword(account);

        account.setPasswordHash(null);
        log.debug("Password set to null for user ID: {}", accountId);
        accountRepository.save(account);

        refreshTokenRepository.deleteRefreshTokenByAccountEmail(accountEmail);
        log.debug(LOG_REFRESH_TOKEN_DELETED, accountEmail);

        String resetMessage = String.format(LinkConstants.RESET_PASSWORD_EMAIL, account.getEmail());
        try {
            userNotificationService.sendPasswordResetRequestEmail(accountEmail, PASSWORD_RESET, resetMessage);
        } catch (MessagingException e) {
            log.error(LOG_SENDING_EMAIL_ERROR, accountEmail, e.getMessage());
            throw new EmailSendException(String.format("Failed to send Password reset request to the email: %s", accountEmail));
        }

        String message = String.format("Password reset request email sent to: %s.", accountEmail);
        log.info(LOG_PASSWORD_HAS_BEEN_RESET, accountId, adminDetails.getAccountId());
        return new SuccessResponse(message);
    }

    private AuthResponse authenticateWith2FA(Account account) {
        UUID accountId = account.getAccountId();
        String accountEmail = account.getEmail();
        log.debug("Account: {} has 2FA enabled. Sending OTP", accountId);

        String otp = passwordGenerator.generateOtp();
        String redisKey = RedisKeyPrefix.LOGIN_OTP.getPrefix() + accountEmail;
        redisService.saveOtp(redisKey, otp, loginOtpExpirationMinutes);
        log.debug("Otp for user with account: {} generated and saved successfully", accountId);

        try {
            userNotificationService.sendVerificationEmail(accountEmail, VERIFICATION_CODE_2FA, otp);
        } catch (MessagingException e) {
            log.error(LOG_SENDING_EMAIL_ERROR, account, e.getMessage());
            throw new EmailSendException(String.format("Failed to send 2FA verification email to the: %s", account));
        }

        log.info("Email with otp sent to email: {}", accountEmail);
        String
            message =
            String.format("Two-factor authentication is required to complete your login. A verification code has been sent "
                          + "to your email: %s. Please enter the code along with your email at the following link: ", accountEmail);
        return AuthResponse.builder()
            .message(message)
            .link(LinkConstants.VERIFY_OTP_LOGIN)
            .build();
    }

    public Authentication createAuthentication(String accountEmail, String password) {
        return authenticationProvider.authenticate(new UsernamePasswordAuthenticationToken(accountEmail, password));
    }

    private void deleteOldTemporaryPassword(Account account) {
        if (account.getTemporaryPassword() != null) {
            passwordRepository.delete(account.getTemporaryPassword());
            account.setTemporaryPassword(null);
            passwordRepository.flush();
            log.debug("Deleted previous temp password for account ID: {}", account.getAccountId());
        }
    }

    private AuthResponse buildAuthResponse(Account account) {
        String newAccessToken = jwtTokenIssuer.generateAccessToken(account);
        log.debug("Access token generated successfully for account: {}", account.getEmail());

        RefreshToken newRefreshToken = jwtTokenIssuer.createRefreshToken(account.getEmail());

        return AuthResponse.builder()
            .tokens(new TokensResponse(newAccessToken, newRefreshToken.getToken(), jwtExpirationS, refreshTokenExpirationS))
            .build();
    }
}
