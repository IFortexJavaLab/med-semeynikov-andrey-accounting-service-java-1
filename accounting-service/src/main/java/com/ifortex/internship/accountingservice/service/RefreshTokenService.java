package com.ifortex.internship.accountingservice.service;

import com.ifortex.internship.accountingservice.model.Account;
import com.ifortex.internship.accountingservice.model.RefreshToken;
import com.ifortex.internship.accountingservice.repository.AccountRepository;
import com.ifortex.internship.accountingservice.repository.RefreshTokenRepository;
import com.ifortex.internship.medstarter.exception.custom.AuthorizationException;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;

    @Value("${app.refreshTokenExpirationS}")
    private final int refreshTokenDurationS;

    @Transactional
    public RefreshToken createRefreshToken(String email) {
        log.debug("Creating refresh token for account: {}", email);
        Account account =
            accountRepository
                .findByEmail(email)
                .orElseThrow(
                    () -> {
                        log.error("Account {} not found", email);
                        return new EntityNotFoundException(String.format("Account %s not found", email));
                    });

        deleteTokenByAccountEmail(email);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAccount(account);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusSeconds(refreshTokenDurationS));

        refreshToken = refreshTokenRepository.save(refreshToken);
        log.debug("Refresh token created for account {}", account.getEmail());
        return refreshToken;
    }

    public void verifyExpiration(RefreshToken refreshToken) {

        log.debug("Verifying refresh token expiration.");

        boolean isTokenExpired = refreshToken.getExpiryDate().isBefore(Instant.now());

        if (isTokenExpired) {
            log.debug(
                "Refresh token has expired. AccountID={}, ExpiryDate={}",
                refreshToken.getAccount().getAccountId(),
                refreshToken.getExpiryDate());

            throw new AuthorizationException("Refresh token has expired.");
        }

        log.debug(
            "Refresh token is valid. AccountID = {}, ExpiryDate = {}",
            refreshToken.getAccount().getAccountId(),
            refreshToken.getExpiryDate());
    }

    public void deleteTokenByAccountEmail(String email) {
        log.debug("Deleting refresh token for user: {}", email);
        refreshTokenRepository.deleteRefreshTokenByAccountEmail(email);
        log.debug("Deleted refresh token for user: {}", email);
    }

    public RefreshToken findByToken(String token) {
        log.debug("Searching for refresh token");
        return refreshTokenRepository
            .findByToken(token)
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "Refresh token not found for the provided value: " + token));
    }
}
