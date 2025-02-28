package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.email.EmailService;
import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EmailAlreadyRegistered;
import com.ifortex.internship.authservice.exception.custom.EmailSendException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.ForbiddenActionException;
import com.ifortex.internship.authservice.exception.custom.InternalAuthServiceException;
import com.ifortex.internship.authservice.exception.custom.InvalidRequestException;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.model.Role;
import com.ifortex.internship.authservice.model.TemporaryPassword;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.model.UserDetailsImpl;
import com.ifortex.internship.authservice.model.constant.RedisKeyPrefix;
import com.ifortex.internship.authservice.model.constant.UserRole;
import com.ifortex.internship.authservice.repository.RefreshTokenRepository;
import com.ifortex.internship.authservice.repository.RoleRepository;
import com.ifortex.internship.authservice.repository.TemporaryPasswordRepository;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.service.RedisService;
import com.ifortex.internship.authservice.service.TokenService;
import com.ifortex.internship.authservice.stripe.exception.StripeServiceException;
import com.ifortex.internship.authserviceapi.dto.request.CreateAdminRequest;
import com.ifortex.internship.authserviceapi.dto.request.CreateClientRequest;
import com.ifortex.internship.authserviceapi.dto.request.LoginRequest;
import com.ifortex.internship.authserviceapi.dto.request.PasswordResetWithOtpDto;
import com.ifortex.internship.authserviceapi.dto.request.RegistrationRequest;
import com.ifortex.internship.authserviceapi.dto.request.VerifyLoginOtpRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.CreateUserResponse;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import com.ifortex.internship.authserviceapi.dto.response.TemporaryPasswordResponse;
import com.ifortex.internship.authserviceapi.dto.response.TokensResponse;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl {

    private static final String LOG_USER_NOT_FOUND = "User with email: {} not found";
    private static final String LOG_REFRESH_TOKEN_DELETED = "Refresh token deleted successfully for user: {}";
    private static final String LOG_EMAIL_ALREADY_REGISTERED = "Email: {} is already registered";
    private static final String LOG_USER_SAVED = "User: {} saved to db successfully";

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL_CHARACTERS = "@$!%*?&#";
    private static final String ALL_ALLOWED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$!%*?&#";

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final EmailService emailService;
    private final RedisService redisService;
    private final CustomAuthenticationProvider authenticationProvider;
    private final TemporaryPasswordRepository passwordRepository;
    private final Environment environment;

    private final Random random = new Random();

    @Value("${app.otp.loginExpirationMinutes}")
    private int loginOtpExpirationMinutes;
    @Value("${app.tempPassword.expirationHours}")
    private int tempPasswordExpirationHours;

    @Transactional
    public void registerUser(RegistrationRequest request) {

        String userEmail = request.getEmail();

        log.debug("Register user: {}", userEmail);

        if (userRepository.findByEmail(userEmail).isPresent()) {
            log.debug(LOG_EMAIL_ALREADY_REGISTERED, userEmail);
            log.info("Failed to register user");
            throw new EmailAlreadyRegistered("Email: " + userEmail + " is already registered.");
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
                .setCreatedAt(LocalDateTime.now(Clock.systemUTC()))
                .setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));

        registerUserInStripe(user);

        userRepository.save(user);
        log.debug(LOG_USER_SAVED, userEmail);

        log.info("User: {} register successfully", userEmail);
    }

    @Transactional
    public CreateUserResponse createClient(CreateClientRequest request) {
        return createUserWithRole(request.getEmail(), UserRole.ROLE_USER);
    }

    @Transactional
    public CreateUserResponse createAdmin(CreateAdminRequest request) {
        log.debug("Creating admin with email: {}", request.getEmail());

        List<String> currentRoles = getUserRolesFromAuthentication();

        boolean isCreatedSuperAdmin = request.isSuper();
        boolean isCreatingSuperAdmin = currentRoles.contains(UserRole.ROLE_SUPER_ADMIN.name());
        if (isCreatedSuperAdmin && !isCreatingSuperAdmin) {
            throw new ForbiddenActionException("Only Super Admin can create another Admin.");
        }

        UserRole role = request.isSuper() ? UserRole.ROLE_SUPER_ADMIN : UserRole.ROLE_ADMIN;
        return createUserWithRole(request.getEmail(), role);
    }

    public AuthResponse authenticateUser(LoginRequest loginRequest) {

        String userEmail = loginRequest.getEmail();
        log.debug("Authenticating user with email: {}", userEmail);

        Authentication
            authentication =
            authenticationProvider.authenticate(new UsernamePasswordAuthenticationToken(userEmail, loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        User user = (User) authentication.getPrincipal();

        log.debug("User: {} successfully authenticated.", userEmail);

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
                log.error("Error during sending 2FA verification email for: {}. StackTrace: {}", userEmail, e.getMessage());
                throw new EmailSendException(String.format("Failed to send 2FA verification email to the: %s", userEmail));
            }

            String verifyOtpLink = environment.getProperty("app.link.verifyOtpLogin");
            String
                message =
                String.format("Two-factor authentication is required to complete your login. A verification code has been sent "
                              + "to your email: %s. Please enter the code along with your email at the following link: ", userEmail);
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

        var user = userRepository.findByEmail(userEmail).orElseThrow(() -> {
            log.debug(LOG_USER_NOT_FOUND, userEmail);
            return new AuthorizationException("Failed to verify otp. Please try again.");
        });

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse logoutUser() {

        String userEmail = getUserEmailFromAuthentication();
        log.info("Logout attempt for user: {}", userEmail);
        refreshTokenRepository.deleteRefreshTokenByUserEmail(userEmail);
        log.debug(LOG_REFRESH_TOKEN_DELETED, userEmail);

        log.info("Logout successful for user: {}", userEmail);
        return AuthResponse.builder().message(String.format("Logout successful for user %s", userEmail)).build();
    }

    public SuccessResponse initiatePasswordReset(String email) {

        log.debug("Initiating password reset for email: {}", email);

        if (userRepository.findByEmail(email).isEmpty()) {
            log.debug(LOG_USER_NOT_FOUND, email);
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
            log.error("Error during sending verification email for: {}. There details: {}", email, e.getMessage());
            throw new EmailSendException(String.format("Failed to send verification email to the: %s", email));
        }

        String resetPasswordLink = environment.getProperty("app.link.resetPasswordConfirm");
        String message = String.format("An email with a password reset code has been sent to your email: %s, please follow this link: ", email);

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

        var user = userRepository.findByEmail(userEmail).orElseThrow(() -> {
            log.debug(LOG_USER_NOT_FOUND, userEmail);
            return new AuthorizationException("Failed to reset password. Please try again.");
        });

        String newEncodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(newEncodedPassword);
        user.setTemporaryPassword(null);
        user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
        userRepository.save(user);

        redisService.deleteOtp(redisKey);

        log.info("User with email: {} successfully changed password", userEmail);

        String loginLink = environment.getProperty("app.link.login");
        String message = "Changed password successfully, please log in again using this link: ";
        return new SuccessResponse(message, loginLink);
    }

    public String generateOtp() {
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    public String getUserEmailFromAuthentication() {
        UserDetailsImpl principle = validateAuthenticatedUser();
        return principle.getEmail();
    }

    public String getUserIdFromAuthentication() {
        UserDetailsImpl principle = validateAuthenticatedUser();
        return principle.getUserId();
    }

    public List<String> getUserRolesFromAuthentication() {
        UserDetailsImpl principle = validateAuthenticatedUser();
        return principle.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Transactional
    public TemporaryPasswordResponse resetPasswordWithTemp(String userId) {
        log.debug("Initiating password reset for user with ID: {}", userId);

        List<String> currentUserRoles = getUserRolesFromAuthentication();
        boolean isCurrentUserSuperAdmin = currentUserRoles.contains(UserRole.ROLE_SUPER_ADMIN.name());

        User user = userRepository.findByUserId(userId).orElseThrow(() -> {
            log.debug("User with ID: {} not found", userId);
            return new EntityNotFoundException(String.format("User with ID: %s not found", userId));
        });

        checkSuperAdminModification(user, isCurrentUserSuperAdmin);

        deleteOldTemporaryPassword(user);

        String tempPassword = generateTempPassword();
        String hashedPassword = passwordEncoder.encode(tempPassword);
        var temporaryPassword =
            new TemporaryPassword(
                user,
                hashedPassword,
                LocalDateTime.now(Clock.systemUTC()).plusHours(tempPasswordExpirationHours));

        user.setPassword(null);
        user.setTemporaryPassword(temporaryPassword);
        user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
        userRepository.save(user);
        log.debug("Temporary password created and saved for user ID: {}", userId);

        refreshTokenRepository.deleteRefreshTokenByUserEmail(user.getEmail());
        log.debug(LOG_REFRESH_TOKEN_DELETED, user.getEmail());

        return new TemporaryPasswordResponse(tempPassword);
    }

    @Transactional
    public SuccessResponse resetPasswordWithEmail(String userId) {
        log.debug("Initiating password reset request email for user with ID: {}", userId);

        List<String> currentUserRoles = getUserRolesFromAuthentication();
        boolean isCurrentUserSuperAdmin = currentUserRoles.contains(UserRole.ROLE_SUPER_ADMIN.name());

        User user = userRepository.findByUserId(userId).orElseThrow(() -> {
            log.debug("User with ID: {} not found", userId);
            return new EntityNotFoundException(String.format("User with ID: %s not found", userId));
        });

        String userEmail = user.getEmail();

        checkSuperAdminModification(user, isCurrentUserSuperAdmin);
        deleteOldTemporaryPassword(user);

        user.setPassword(null);
        user.setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));
        userRepository.save(user);
        log.debug("Password set to null for user ID: {}", userId);

        refreshTokenRepository.deleteRefreshTokenByUserEmail(userEmail);
        log.debug(LOG_REFRESH_TOKEN_DELETED, userEmail);

        String resetLinkTemplate = environment.getProperty("app.link.resetPasswordEmail", String.class);
        assert resetLinkTemplate != null;
        String resetLink = String.format(resetLinkTemplate, userEmail);
        try {
            emailService.sendPasswordResetRequestEmail(userEmail, "Password reset", resetLink);
        } catch (MessagingException e) {
            log.error("Error during sending 2FA verification email for: {}. StackTrace: {}", userEmail, e.getMessage());
            throw new EmailSendException(String.format("Failed to send Password reset request to the email: %s", userEmail));
        }

        String message = String.format("Password reset request email sent to: %s.", userEmail);
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

    private void checkSuperAdminModification(User user, boolean isCurrentUserSuperAdmin) {
        boolean isEditedUserSuperAdmin = user.getRoles().stream().anyMatch(role -> role.getName() == UserRole.ROLE_SUPER_ADMIN);

        if (isEditedUserSuperAdmin && !isCurrentUserSuperAdmin) {
            log.debug("Attempt to reset password for ROLE_SUPER_ADMIN with ROLE_ADMIN");
            throw new ForbiddenActionException("You can't edit user with ROLE_SUPER_ADMIN");
        }
    }

    private void deleteOldTemporaryPassword(User user) {
        if (user.getTemporaryPassword() != null) {
            passwordRepository.delete(user.getTemporaryPassword());
            user.setTemporaryPassword(null);
            passwordRepository.flush();
            log.debug("Deleted previous temp password for user ID: {}", user.getUserId());
        }
    }

    private AuthResponse buildAuthResponse(User user) {

        String newAccessToken = tokenService.generateAccessToken(user);
        log.debug("Access token generated successfully for user: {}", user.getEmail());

        RefreshToken newRefreshToken = tokenService.createRefreshToken(user.getEmail());

        Long jwtExpirationS = Long.valueOf(Objects.requireNonNull(environment.getProperty("app.jwtExpirationS")));
        Long refreshTokenExpirationS = Long.valueOf(Objects.requireNonNull(environment.getProperty("app.refreshTokenExpirationS")));

        return AuthResponse.builder().tokens(new TokensResponse(newAccessToken, newRefreshToken.getToken(), jwtExpirationS, refreshTokenExpirationS))
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

    private void registerUserInStripe(User user) {
        CustomerCreateParams customerParams = CustomerCreateParams.builder().setEmail(user.getEmail()).build();
        Customer customer;
        try {
            customer = Customer.create(customerParams);
        } catch (StripeException e) {
            log.error("Stripe API call failed: {}. Error code: {}. StackTrace: ", e.getMessage(), e.getCode(), e);
            throw new StripeServiceException("Error occurred while registration. Please try again later");
        }
        user.setStripeCustomerId(customer.getId());
        log.debug("Generated and saved StripeCustomerId: {} for user with ID: {}", customer.getId(), user.getUserId());
    }

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
                .setCreatedAt(LocalDateTime.now(Clock.systemUTC()))
                .setUpdatedAt(LocalDateTime.now(Clock.systemUTC()));

        TemporaryPassword temporaryPassword =
            new TemporaryPassword(user, hashedPassword,
                LocalDateTime.now(Clock.systemUTC()).plusHours(tempPasswordExpirationHours));

        user.setTemporaryPassword(temporaryPassword);
        userRepository.save(user);

        log.debug(LOG_USER_SAVED, email);

        if (role.equals(UserRole.ROLE_USER)) {
            registerUserInStripe(user);
        }

        log.info("User: {} created successfully", email);

        String message = String.format("User: %s created successfully", user.getEmail());
        return new CreateUserResponse(message, password, tempPasswordExpirationHours);
    }

    private void validateEmailNotRegistered(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            log.debug(LOG_EMAIL_ALREADY_REGISTERED, email);
            throw new EmailAlreadyRegistered("Email: " + email + " is already registered.");
        }
    }

    private Role getRoleForUserType(UserRole role, String email) {
        return roleRepository
            .findByName(role)
            .orElseThrow(
                () -> {
                    log.debug("User Role: {} not found", role);
                    return new InternalAuthServiceException(
                        String.format("Failed to register user with email: %s", email));
                });
    }
}
