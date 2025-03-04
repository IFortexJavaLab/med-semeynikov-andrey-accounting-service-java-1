package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.dto.AdminDetailsDto;
import com.ifortex.internship.authservice.dto.CreatedAccountDto;
import com.ifortex.internship.authservice.email.EmailService;
import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EmailAlreadyRegistered;
import com.ifortex.internship.authservice.exception.custom.EmailSendException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.ForbiddenActionException;
import com.ifortex.internship.authservice.exception.custom.InvalidRequestException;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.model.TemporaryPassword;
import com.ifortex.internship.authservice.model.UserDetailsImpl;
import com.ifortex.internship.authservice.model.constant.RedisKeyPrefix;
import com.ifortex.internship.authservice.model.constant.RoleType;
import com.ifortex.internship.authservice.repository.AccountRepository;
import com.ifortex.internship.authservice.repository.RefreshTokenRepository;
import com.ifortex.internship.authservice.repository.TemporaryPasswordRepository;
import com.ifortex.internship.authserviceapi.dto.request.LoginRequest;
import com.ifortex.internship.authserviceapi.dto.request.VerifyLoginOtpRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import com.ifortex.internship.authserviceapi.dto.response.TemporaryPasswordResponse;
import com.ifortex.internship.authserviceapi.dto.response.TokensResponse;
import jakarta.mail.MessagingException;
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

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

//todo split this class
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String LOG_ACCOUNT_NOT_FOUND = "User with email: {} not found";
    private static final String LOG_REFRESH_TOKEN_DELETED = "Refresh token deleted successfully for user: {}";
    private static final String LOG_EMAIL_ALREADY_REGISTERED = "Email: {} is already registered";

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARACTERS = "@$!%*?&#";
    private static final String ALL_ALLOWED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$!%*?&#";

    private static final int ROLE_LENGTH = 5;

    private final CustomAuthenticationProvider authenticationProvider;
    private final TemporaryPasswordRepository passwordRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final RedisService redisService;
    private final Environment environment;

    private final Random random = new Random();

    @Value("${app.otp.loginExpirationMinutes}")
    private int loginOtpExpirationMinutes;
    @Value("${app.tempPassword.expirationHours}")
    private int tempPasswordExpirationHours;
    @Value("${app.link.resetPasswordEmail}")
    private String resetLink;

    //todo maybe transfer to clientService

    public AuthResponse authenticateUser(LoginRequest loginRequest) {

        String accountEmail = loginRequest.getEmail();
        log.debug("Authenticating user with email: {}", accountEmail);

        Authentication
            authentication =
            authenticationProvider.authenticate(new UsernamePasswordAuthenticationToken(accountEmail, loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        Account account = (Account) authentication.getPrincipal();

        log.debug("User: {} successfully authenticated.", accountEmail);

        if (account.isTwoFactorEnabled()) {
            log.debug("User: {} has 2FA enabled. Sending OTP", accountEmail);

            String otp = generateOtp();
            String redisKey = RedisKeyPrefix.LOGIN_OTP.getPrefix() + accountEmail;
            redisService.saveOtp(redisKey, otp, loginOtpExpirationMinutes);
            log.debug("Otp for user: {} generated and saved successfully", accountEmail);

            // feature refactor method with dotry
            try {
                emailService.sendVerificationEmail(accountEmail, "2FA Verification Code", otp);
            } catch (MessagingException e) {
                log.error("Error during sending 2FA verification email for: {}. StackTrace: {}", accountEmail, e.getMessage());
                throw new EmailSendException(String.format("Failed to send 2FA verification email to the: %s", accountEmail));
            }

            String verifyOtpLink = environment.getProperty("app.link.verifyOtpLogin");
            String
                message =
                String.format("Two-factor authentication is required to complete your login. A verification code has been sent "
                              + "to your email: %s. Please enter the code along with your email at the following link: ", accountEmail);
            return AuthResponse.builder().message(message).link(verifyOtpLink).build();
        }

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
            log.debug(LOG_ACCOUNT_NOT_FOUND, accountEmail);
            return new AuthorizationException("Failed to verify otp. Please try again.");
        });

        return buildAuthResponse(account);
    }

    @Transactional
    public AuthResponse logoutUser() {

        String userEmail = getUserEmailFromAuthentication();
        log.info("Logout attempt for user: {}", userEmail);
        refreshTokenRepository.deleteRefreshTokenByAccountEmail(userEmail);
        log.debug(LOG_REFRESH_TOKEN_DELETED, userEmail);

        log.info("Logout successful for user: {}", userEmail);
        return AuthResponse.builder().message(String.format("Logout successful for user %s", userEmail)).build();
    }

    public String getUserEmailFromAuthentication() {
        UserDetailsImpl principle = validateAuthenticatedUser();
        return principle.getEmail();
    }

    public UUID getAccountIdFromAuthentication() {
        UserDetailsImpl principle = validateAuthenticatedUser();
        return principle.getAccountId();
    }

    public List<String> getUserRolesFromAuthentication() {
        UserDetailsImpl principle = validateAuthenticatedUser();
        return principle.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    public AdminDetailsDto getAdminDetailsFromAuthentication() {
        UserDetailsImpl principle = validateAuthenticatedUser();
        List<RoleType> role = principle.
            getAuthorities().stream()
            .map(authority ->
                RoleType.valueOf(authority.getAuthority().substring(ROLE_LENGTH)))
            .toList();

        RoleType roleType = null;
        if (!role.isEmpty()) {
            roleType = role.getFirst();
        }
        return new AdminDetailsDto(principle.getAccountId(),
            principle.getEmail(), roleType, principle.getIsSuperAdmin());
    }

    @Transactional
    public TemporaryPasswordResponse resetPasswordWithTemp(UUID accountId) {

        log.debug("Initiating password reset for user with account ID: {}", accountId);
        AdminDetailsDto adminDetails = getAdminDetailsFromAuthentication();

        Account account = accountRepository.findByAccountId(accountId).orElseThrow(() -> {
            log.error("Account with ID: {} not found", accountId);
            return new EntityNotFoundException(String.format("Account with ID: %s not found", accountId));
        });

        validateUserModificationPermission(adminDetails, account);

        deleteOldTemporaryPassword(account);

        String tempPassword = generateTempPassword();
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

        log.info("Password has been reset for user: {} by admin: {}", accountId, adminDetails.getAccountId());

        return new TemporaryPasswordResponse(tempPassword, tempPasswordExpirationHours);
    }

    public void validateUserModificationPermission(AdminDetailsDto changer, Account targetAccount) {

        UUID editorAccountId = changer.getAccountId();
        UUID targetAccountId = targetAccount.getAccountId();
        RoleType targetRole = targetAccount.getAccountRole().getRoleType();

        log.debug("Validating user modification permission. Editor ID: {}, Target ID: {}, Target Role: {}",
            editorAccountId, targetAccountId, targetRole);

        if (changer.isSuperAdmin()) {
            log.debug("Editor with ID: {} is a super admin. Modification allowed.", editorAccountId);
            return;
        }

        boolean
            isTargetUserClientOrParamedic =
            targetAccount.getAccountRole().getRoleType().equals(RoleType.CLIENT) ||
            targetAccount.getAccountRole().getRoleType().equals(RoleType.PARAMEDIC);

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

        AdminDetailsDto adminDetails = getAdminDetailsFromAuthentication();

        Account account = accountRepository.findByAccountId(accountId).orElseThrow(() -> {
            log.error("Account with ID: {} not found", accountId);
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

        String resetMessage = String.format(resetLink, account.getEmail());
        try {
            emailService.sendPasswordResetRequestEmail(account.getEmail(), "Password reset", resetMessage);
        } catch (MessagingException e) {
            log.error("Error during sending 2FA verification email for: {}. StackTrace: {}", accountEmail, e.getMessage());
            throw new EmailSendException(String.format("Failed to send Password reset request to the email: %s", accountEmail));
        }

        String message = String.format("Password reset request email sent to: %s.", accountEmail);
        log.info("Password has been reset for user: {} by admin: {}", accountId, adminDetails.getAccountId());
        return new SuccessResponse(message);
    }

    private UserDetailsImpl validateAuthenticatedUser() {
        Object principle = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if ("anonymousUser".equals(principle.toString())) {
            log.debug("Attempt to get user details by anonymous or unauthenticated user.");
            throw new AuthorizationException("User is not authenticated. Please log in.");
        }
        return (UserDetailsImpl) principle;
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

        String newAccessToken = tokenService.generateAccessToken(account);
        log.debug("Access token generated successfully for account: {}", account.getEmail());

        RefreshToken newRefreshToken = tokenService.createRefreshToken(account.getEmail());

        Long jwtExpirationS = Long.valueOf(Objects.requireNonNull(environment.getProperty("app.jwtExpirationS")));
        Long refreshTokenExpirationS = Long.valueOf(Objects.requireNonNull(environment.getProperty("app.refreshTokenExpirationS")));

        return AuthResponse.builder()
            .tokens(new TokensResponse(newAccessToken, newRefreshToken.getToken(), jwtExpirationS, refreshTokenExpirationS))
            .build();
    }

    private String generateTempPassword() {

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

    public void validateEmailNotRegistered(String email) {
        if (accountRepository.findByEmail(email).isPresent()) {
            log.error(LOG_EMAIL_ALREADY_REGISTERED, email);
            throw new EmailAlreadyRegistered("Email: " + email + " is already registered.");
        }
    }

    public CreatedAccountDto createAccount(String email, String password) {

        Account account =
            new Account()
                .setAccountId(UUID.randomUUID())
                .setEmail(email);

        String hashedPassword = null;
        if (password == null) {

            password = generateTempPassword();
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

    public String generateOtp() {
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }
}
