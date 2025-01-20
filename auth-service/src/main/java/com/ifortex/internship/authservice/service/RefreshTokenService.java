package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.model.RefreshToken;

/**
 * Service interface for managing refresh tokens.
 *
 * <p>Provides functionality for creating, verifying, and deleting refresh tokens, as well as
 * retrieving tokens by their value.
 */
public interface RefreshTokenService {

  /**
   * Creates a new refresh token for the specified user.
   *
   * <p>Deletes any existing tokens for the user before creating a new one.
   *
   * @param userId the ID of the user for whom the refresh token is created
   * @return the created {@link RefreshToken}
   * @throws EntityNotFoundException if no user is found with the given ID
   */
  RefreshToken createRefreshToken(Long userId);

  /**
   * Verifies the expiration date of the given refresh token.
   *
   *
   * @param refreshToken the RefreshToken to verify
   * @return the verified RefreshToken if it is still valid
   * @throws AuthorizationException if the token has expired
   */
  RefreshToken verifyExpiration(RefreshToken refreshToken);

  /**
   * Deletes all refresh tokens associated with the specified user.
   *
   * @param userId the ID of the user whose tokens should be deleted
   */
  void deleteTokensByUserId(Long userId);

  /**
   * Retrieves a refresh token by its value.
   *
   * <p>Throws an exception if the token is not found or invalid.
   *
   * @param token the token value to search for
   * @return the found RefreshToken
   * @throws EntityNotFoundException if the token is not found
   */
  RefreshToken findByToken(String token);

  /**
   * Deletes the specified refresh token.
   *
   * @param token the RefreshToken to delete
   */
  void deleteToken(RefreshToken token);
}
