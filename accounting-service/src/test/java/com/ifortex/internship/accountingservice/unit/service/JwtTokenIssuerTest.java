package com.ifortex.internship.accountingservice.unit.service;

import com.ifortex.internship.accountingservice.dto.response.TokensResponse;
import com.ifortex.internship.accountingservice.model.Account;
import com.ifortex.internship.accountingservice.model.Admin;
import com.ifortex.internship.accountingservice.model.Client;
import com.ifortex.internship.accountingservice.model.RefreshToken;
import com.ifortex.internship.accountingservice.model.Role;
import com.ifortex.internship.accountingservice.model.stripe.StripeSubscription;
import com.ifortex.internship.accountingservice.model.stripe.SubscriptionStatus;
import com.ifortex.internship.accountingservice.repository.AdminRepository;
import com.ifortex.internship.accountingservice.repository.ClientRepository;
import com.ifortex.internship.accountingservice.service.JwtTokenIssuer;
import com.ifortex.internship.accountingservice.service.RefreshTokenService;
import com.ifortex.internship.medstarter.exception.custom.AuthorizationException;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import com.ifortex.internship.medstarter.exception.custom.UserBlockedException;
import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import com.ifortex.internship.medstarter.security.service.JwtTokenValidator;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenIssuerTest {

    @Mock RefreshTokenService refreshTokenService;
    @Mock ClientRepository clientRepository;
    @Mock AdminRepository adminRepository;
    @Mock JwtTokenValidator jwtTokenValidator;

    JwtTokenIssuer jwtTokenIssuer;

    @BeforeEach
    void setUp() {
        String base64Secret = Base64.getEncoder().encodeToString("this-is-a-very-secure-secret-key!".getBytes(StandardCharsets.UTF_8));
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        SecretKey realSecretKey = Keys.hmacShaKeyFor(keyBytes);

        lenient().when(jwtTokenValidator.getSigningKey()).thenReturn(realSecretKey);

        jwtTokenIssuer = new JwtTokenIssuer(
            refreshTokenService,
            clientRepository,
            adminRepository,
            jwtTokenValidator,
            3600L,
            7200L
        );
    }

    @Test
    void generateAccessToken_shouldIncludeSubscription_whenClientHasActiveSubscription() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account().setAccountId(accountId)
            .setEmail("client@example.com")
            .setRole(new Role(2L, UserRole.CLIENT))
            .setFirstName("Anna");

        StripeSubscription subscription = new StripeSubscription();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setEndDate(Instant.now().plus(Duration.ofHours(1)));

        Client client = new Client();
        client.setSubscriptions(List.of(subscription));

        when(clientRepository.findByAccountId(accountId)).thenReturn(Optional.of(client));

        String token = jwtTokenIssuer.generateAccessToken(account);

        assertNotNull(token);
    }

    @Test
    void generateAccessToken_shouldIncludeSuperAdminFlag_whenAdmin() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account().setAccountId(accountId)
            .setEmail("admin@example.com")
            .setRole(new Role(2L, UserRole.ADMIN))
            .setFirstName("John");

        Admin admin = new Admin();
        admin.setSuperAdmin(true);

        when(adminRepository.findByAccountId(accountId)).thenReturn(Optional.of(admin));

        String token = jwtTokenIssuer.generateAccessToken(account);

        assertNotNull(token);
    }

    @Test
    void generateAccessToken_shouldThrow_whenClientNotFound() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account().setAccountId(accountId)
            .setEmail("missing@client.com")
            .setRole(new Role(2L, UserRole.CLIENT));

        when(clientRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
            () -> jwtTokenIssuer.generateAccessToken(account));
    }

    @Test
    void generateAccessToken_shouldThrow_whenAdminNotFound() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account().setAccountId(accountId)
            .setEmail("admin@notfound.com")
            .setRole(new Role(1L, UserRole.ADMIN));

        when(adminRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
            () -> jwtTokenIssuer.generateAccessToken(account));
    }

    @Test
    void refreshTokens_shouldReturnNewTokens_whenValidRefreshToken() {
        String oldToken = "refresh-token";
        UUID accountId = UUID.randomUUID();
        Account account = new Account().setAccountId(accountId)
            .setEmail("user@example.com")
            .setRole(new Role(1L, UserRole.ADMIN));

        RefreshToken oldRefreshToken = new RefreshToken();
        oldRefreshToken.setToken(oldToken);
        oldRefreshToken.setAccount(account);
        oldRefreshToken.setExpiryDate(Instant.now().plusSeconds(3600));

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setToken("new-token");
        newRefreshToken.setAccount(account);
        newRefreshToken.setExpiryDate(Instant.now().plusSeconds(7200));

        when(refreshTokenService.findByToken(oldToken)).thenReturn(oldRefreshToken);
        doNothing().when(refreshTokenService).verifyExpiration(oldRefreshToken);
        when(refreshTokenService.createRefreshToken(account.getEmail())).thenReturn(newRefreshToken);
        when(adminRepository.findByAccountId(accountId)).thenReturn(Optional.of(new Admin()));
        // when(jwtTokenValidator.getSigningKey()).thenReturn(signingKey);

        TokensResponse tokens = jwtTokenIssuer.refreshTokens(oldToken);

        assertEquals("new-token", tokens.getRefreshToken());
        assertEquals(3600L, tokens.getAccessTokenExpirationS());
        assertEquals(7200L, tokens.getRefreshTokenExpirationS());
    }

    @Test
    void refreshTokens_shouldThrow_whenTokenIsExpired() {
        String token = "expired-token";
        when(refreshTokenService.findByToken(token)).thenThrow(new AuthorizationException("expired"));

        AuthorizationException ex = assertThrows(AuthorizationException.class,
            () -> jwtTokenIssuer.refreshTokens(token));

        assertEquals("Your session has expired. Please log in again.", ex.getMessage());
    }

    @Test
    void refreshTokens_shouldThrow_whenAccountBlocked() {
        String token = "blocked-token";
        Account account = new Account()
            .setBlockedUntil(Instant.now().plus(Duration.ofMinutes(10)))
            .setAccountId(UUID.randomUUID())
            .setEmail("blocked@example.com")
            .setRole(new Role(2L, UserRole.CLIENT));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAccount(account);
        refreshToken.setExpiryDate(Instant.now().plusSeconds(600));

        when(refreshTokenService.findByToken(token)).thenReturn(refreshToken);
        doNothing().when(refreshTokenService).verifyExpiration(refreshToken);

        UserBlockedException ex = assertThrows(UserBlockedException.class,
            () -> jwtTokenIssuer.refreshTokens(token));

        assertTrue(ex.getMessage().contains("Your account is blocked"));
    }

    @Test
    void createRefreshToken_shouldDelegateToService() {
        String email = "delegate@example.com";
        RefreshToken expected = new RefreshToken();
        when(refreshTokenService.createRefreshToken(email)).thenReturn(expected);

        RefreshToken actual = jwtTokenIssuer.createRefreshToken(email);

        assertSame(expected, actual);
    }
}
