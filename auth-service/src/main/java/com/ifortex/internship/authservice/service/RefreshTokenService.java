package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.repository.AccountRepository;
import com.ifortex.internship.authservice.repository.RefreshTokenRepository;
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
//todo logs to string variables
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;

    private static final String LOG_CREATING_REFRESH_TOKEN = "Creating refresh token for account: {}";
    private static final String LOG_ACCOUNT_NOT_FOUND = "Account {} not found";

    @Value("${app.refreshTokenExpirationS}")
    private int refreshTokenDurationS;

    @Transactional
    public RefreshToken createRefreshToken(String email) {
        log.debug(LOG_CREATING_REFRESH_TOKEN, email);
        Account account =
            accountRepository
                .findByEmail(email)
                .orElseThrow(
                    () -> {
                        log.error(LOG_ACCOUNT_NOT_FOUND, email);
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
