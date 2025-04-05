package com.ifortex.internship.accountingservice.unit.service;

import com.ifortex.internship.accountingservice.dto.request.LoginRequest;
import com.ifortex.internship.accountingservice.dto.request.VerifyLoginOtpRequest;
import com.ifortex.internship.accountingservice.dto.response.AuthResponse;
import com.ifortex.internship.accountingservice.dto.response.SuccessResponse;
import com.ifortex.internship.accountingservice.dto.response.TemporaryPasswordResponse;
import com.ifortex.internship.accountingservice.model.Account;
import com.ifortex.internship.accountingservice.model.RefreshToken;
import com.ifortex.internship.accountingservice.model.Role;
import com.ifortex.internship.accountingservice.repository.AccountRepository;
import com.ifortex.internship.accountingservice.repository.RefreshTokenRepository;
import com.ifortex.internship.accountingservice.repository.TemporaryPasswordRepository;
import com.ifortex.internship.accountingservice.service.AuthService;
import com.ifortex.internship.accountingservice.service.CustomAuthenticationProvider;
import com.ifortex.internship.accountingservice.service.JwtTokenIssuer;
import com.ifortex.internship.accountingservice.service.RedisService;
import com.ifortex.internship.accountingservice.service.UserNotificationService;
import com.ifortex.internship.accountingservice.util.PasswordGenerator;
import com.ifortex.internship.medstarter.exception.custom.EmailSendException;
import com.ifortex.internship.medstarter.exception.custom.InvalidRequestException;
import com.ifortex.internship.medstarter.model.constant.LinkConstants;
import com.ifortex.internship.medstarter.security.dto.AdminDetailsDto;
import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import com.ifortex.internship.medstarter.security.service.AuthenticationFacade;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock JwtTokenIssuer jwtTokenIssuer;
    @Mock RedisService redisService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AccountRepository accountRepository;
    @Mock PasswordGenerator passwordGenerator;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock TemporaryPasswordRepository passwordRepository;
    @Mock CustomAuthenticationProvider authenticationProvider;
    @Mock AuthenticationFacade authenticationFacade;
    @Mock UserNotificationService userNotificationService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            jwtTokenIssuer,
            redisService,
            passwordEncoder,
            accountRepository,
            passwordGenerator,
            refreshTokenRepository,
            passwordRepository,
            authenticationProvider,
            authenticationFacade,
            userNotificationService,
            3600L,
            5,
            24,
            7200L
        );
    }

    @Test
    void authenticateUser_shouldTrigger2FA_whenTwoFactorEnabled() {
        String email = "2fa@example.com";
        String password = "Secret123!";
        LoginRequest request = new LoginRequest(email, password);

        Account mockAccount = new Account()
            .setAccountId(UUID.randomUUID())
            .setEmail(email)
            .setTwoFactorEnabled(true);

        Authentication authentication = new UsernamePasswordAuthenticationToken(mockAccount, null);

        when(authenticationProvider.authenticate(any())).thenReturn(authentication);
        when(passwordGenerator.generateOtp()).thenReturn("123456");
        doNothing().when(redisService).saveOtp("login_otp:2fa@example.com", "123456", 5L);

        AuthResponse response = authService.authenticateUser(request);

        assertTrue(response.getMessage().contains("Two-factor authentication is required"));
        assertEquals(LinkConstants.VERIFY_OTP_LOGIN, response.getLink());
    }

    @Test
    void completeLoginWithOtp_shouldReturnTokens_whenOtpIsValid() {
        String email = "otp@valid.com";
        String otp = "123456";
        VerifyLoginOtpRequest request = new VerifyLoginOtpRequest();
        request.setEmail(email);
        request.setOtp(otp);

        Account account = new Account()
            .setAccountId(UUID.randomUUID())
            .setEmail(email);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refreshToken");
        refreshToken.setAccount(account);
        refreshToken.setExpiryDate(Instant.now().plusSeconds(3600));

        when(redisService.getOtp("login_otp:" + email)).thenReturn(otp);
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(jwtTokenIssuer.generateAccessToken(account)).thenReturn("accessToken");
        when(jwtTokenIssuer.createRefreshToken(email)).thenReturn(refreshToken);

        AuthResponse response = authService.completeLoginWithOtp(request);

        assertNotNull(response.getTokens());
        assertEquals("accessToken", response.getTokens().getAccessToken());
        assertEquals("refreshToken", response.getTokens().getRefreshToken());
    }

    @Test
    void resetPasswordWithTemp_shouldReturnNewPassword_whenValid() {
        UUID accountId = UUID.randomUUID();
        String email = "reset@pass.com";

        Account account = new Account().setAccountId(accountId).setEmail(email).setRole(new Role(1L, UserRole.ADMIN));
        AdminDetailsDto admin = new AdminDetailsDto(UUID.randomUUID(), "admin@mail.com", UserRole.ADMIN, true);

        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));
        when(authenticationFacade.getAdminDetailsFromAuthentication()).thenReturn(admin);
        when(passwordGenerator.generateTempPassword()).thenReturn("NewPass123!");
        when(passwordEncoder.encode("NewPass123!")).thenReturn("encoded-pass");

        TemporaryPasswordResponse response = authService.resetPasswordWithTemp(accountId);

        assertEquals("NewPass123!", response.getTempPassword());
        assertEquals(24, response.getPasswordExpirationH());
        verify(refreshTokenRepository).deleteRefreshTokenByAccountEmail(email);
    }

    @Test
    void completeLoginWithOtp_shouldThrow_whenOtpInvalid() {
        String email = "user@wrong.com";
        String wrongOtp = "000000";
        VerifyLoginOtpRequest request = new VerifyLoginOtpRequest();
        request.setEmail(email);
        request.setOtp(wrongOtp);

        when(redisService.getOtp("login_otp:" + email)).thenReturn("654321");

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
            () -> authService.completeLoginWithOtp(request));

        assertEquals("OTP has expired or is invalid. Please try again.", ex.getMessage());
    }

    @Test
    void logoutUser_shouldDeleteRefreshTokenAndReturnSuccessMessage() {
        String userEmail = "user@example.com";
        when(authenticationFacade.getUserEmailFromAuthentication()).thenReturn(userEmail);

        AuthResponse response = authService.logoutUser();

        verify(authenticationFacade).getUserEmailFromAuthentication();
        verify(refreshTokenRepository).deleteRefreshTokenByAccountEmail(userEmail);

        assertNotNull(response);
        assertEquals("Logout successful for user user@example.com", response.getMessage());
    }

    @Test
    void resetPasswordWithEmail_shouldSendEmailAndReturnSuccess_whenValid() throws MessagingException {
        UUID accountId = UUID.randomUUID();
        String email = "user@example.com";
        Account account = new Account().setAccountId(accountId).setEmail(email).setRole(new Role(1L, UserRole.ADMIN));
        AdminDetailsDto admin = new AdminDetailsDto(UUID.randomUUID(), "admin@mail.com", UserRole.ADMIN, true);

        when(authenticationFacade.getAdminDetailsFromAuthentication()).thenReturn(admin);
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));
        doNothing().when(userNotificationService)
            .sendPasswordResetRequestEmail(eq(email), eq("Password reset"), anyString());

        SuccessResponse response = authService.resetPasswordWithEmail(accountId);

        verify(passwordRepository, atLeast(0)).flush();
        verify(accountRepository).save(account);
        verify(refreshTokenRepository).deleteRefreshTokenByAccountEmail(email);
        verify(userNotificationService)
            .sendPasswordResetRequestEmail(eq(email), eq("Password reset"), anyString());

        assertEquals("Password reset request email sent to: user@example.com.", response.getMessage());
    }

    @Test
    void resetPasswordWithEmail_shouldThrow_whenEmailSendingFails() throws MessagingException {
        UUID accountId = UUID.randomUUID();
        String email = "user@example.com";
        Account account = new Account().setAccountId(accountId).setEmail(email).setRole(new Role(1L, UserRole.ADMIN));
        AdminDetailsDto admin = new AdminDetailsDto(UUID.randomUUID(), "admin@mail.com", UserRole.ADMIN, true);

        when(authenticationFacade.getAdminDetailsFromAuthentication()).thenReturn(admin);
        when(accountRepository.findByAccountId(accountId)).thenReturn(Optional.of(account));
        doThrow(new MessagingException("Simulated fail"))
            .when(userNotificationService)
            .sendPasswordResetRequestEmail(eq(email), eq("Password reset"), anyString());

        EmailSendException ex = assertThrows(EmailSendException.class,
            () -> authService.resetPasswordWithEmail(accountId));

        assertEquals("Failed to send Password reset request to the email: user@example.com", ex.getMessage());
    }

}