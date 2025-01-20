package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.repository.RefreshTokenRepository;
import com.ifortex.internship.authservice.repository.UserRepository;
import com.ifortex.internship.authservice.service.RefreshTokenService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;

  @Value("${app.refreshTokenExpirationS}")
  private int refreshTokenDurationS;

  @Transactional
  public RefreshToken createRefreshToken(Long userId) {
    log.debug("Creating refresh token for user with ID: {}", userId);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> {
                  log.debug("User with ID: {} not found", userId);
                  return new EntityNotFoundException(
                      String.format("User with ID: %d not found", userId));
                });

    deleteTokensByUserId(userId);

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUser(user);
    refreshToken.setToken(UUID.randomUUID().toString());
    refreshToken.setExpiryDate(Instant.now().plusSeconds(refreshTokenDurationS));

    refreshToken = refreshTokenRepository.save(refreshToken);
    log.debug("Refresh token created for user {}", user.getEmail());
    return refreshToken;
  }

  public RefreshToken verifyExpiration(RefreshToken refreshToken) {

    log.debug("Verifying refresh token expiration.");

    boolean isTokenExpired = refreshToken.getExpiryDate().isBefore(Instant.now());

    if (isTokenExpired) {
      log.warn(
          "Refresh token has expired. UserId={}, ExpiryDate={}",
          refreshToken.getUser().getId(),
          refreshToken.getExpiryDate());
      log.debug("Deleting refresh token from db");
      refreshTokenRepository.delete(refreshToken);
      log.debug("Refresh token has been deleted from db");

      throw new AuthorizationException("Refresh token has expired.");
    }

    log.debug(
        "Refresh token is valid. UserId = {}, ExpiryDate = {}",
        refreshToken.getUser().getId(),
        refreshToken.getExpiryDate());
    return refreshToken;
  }

  @Transactional
  public void deleteTokensByUserId(Long userId) {
    log.debug("Deleting refresh tokens for user with ID: {}", userId);
    refreshTokenRepository.deleteByUserId(userId);
    log.debug("Deleted all refresh tokens for user with ID: {}", userId);
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

  public void deleteToken(RefreshToken token) {
    refreshTokenRepository.delete(token);
  }
}
