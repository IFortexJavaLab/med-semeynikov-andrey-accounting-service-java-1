package com.ifortex.internship.accountingservice.unit.service;

import com.ifortex.internship.accountingservice.model.Account;
import com.ifortex.internship.accountingservice.model.RefreshToken;
import com.ifortex.internship.accountingservice.repository.AccountRepository;
import com.ifortex.internship.accountingservice.repository.RefreshTokenRepository;
import com.ifortex.internship.accountingservice.service.RefreshTokenService;
import com.ifortex.internship.medstarter.exception.custom.AuthorizationException;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock AccountRepository accountRepository;

    RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, accountRepository, 3600);
    }

    @Test
    void createRefreshToken_shouldCreateAndReturnToken_whenAccountExists() {
        String email = "user@example.com";
        Account account = new Account().setEmail(email);
        RefreshToken savedToken = new RefreshToken();
        savedToken.setToken("test-token");
        savedToken.setAccount(account);
        savedToken.setExpiryDate(Instant.now().plusSeconds(3600));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(account));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(savedToken);

        RefreshToken result = refreshTokenService.createRefreshToken(email);

        verify(refreshTokenRepository).deleteRefreshTokenByAccountEmail(email);
        verify(refreshTokenRepository).save(any(RefreshToken.class));

        assertEquals("test-token", result.getToken());
        assertEquals(account, result.getAccount());
    }

    @Test
    void createRefreshToken_shouldThrow_whenAccountNotFound() {
        String email = "missing@example.com";

        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
            () -> refreshTokenService.createRefreshToken(email));

        assertEquals("Account missing@example.com not found", ex.getMessage());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void verifyExpiration_shouldThrow_whenTokenExpired() {
        Account account = new Account().setAccountId(UUID.randomUUID());
        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setAccount(account);
        expiredToken.setExpiryDate(Instant.now().minusSeconds(10));

        AuthorizationException ex = assertThrows(AuthorizationException.class,
            () -> refreshTokenService.verifyExpiration(expiredToken));

        assertEquals("Refresh token has expired.", ex.getMessage());
    }

    @Test
    void verifyExpiration_shouldPass_whenTokenValid() {
        Account account = new Account().setAccountId(UUID.randomUUID());
        RefreshToken token = new RefreshToken();
        token.setAccount(account);
        token.setExpiryDate(Instant.now().plusSeconds(600));

        assertDoesNotThrow(() -> refreshTokenService.verifyExpiration(token));
    }

    @Test
    void deleteTokenByAccountEmail_shouldCallRepository() {
        String email = "delete@example.com";

        refreshTokenService.deleteTokenByAccountEmail(email);
        verify(refreshTokenRepository).deleteRefreshTokenByAccountEmail(email);
    }

    @Test
    void findByToken_shouldReturnToken_whenFound() {
        String tokenStr = "abc123";
        RefreshToken token = new RefreshToken();
        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.of(token));

        RefreshToken result = refreshTokenService.findByToken(tokenStr);
        assertSame(token, result);
    }

    @Test
    void findByToken_shouldThrow_whenNotFound() {
        String tokenStr = "notfound123";
        when(refreshTokenRepository.findByToken(tokenStr)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
            () -> refreshTokenService.findByToken(tokenStr));
        assertEquals("Refresh token not found for the provided value: notfound123", ex.getMessage());
    }
}
