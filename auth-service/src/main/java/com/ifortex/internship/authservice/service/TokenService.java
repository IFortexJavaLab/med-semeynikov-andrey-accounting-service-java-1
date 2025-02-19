package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authserviceapi.dto.response.TokensResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.security.core.GrantedAuthority;

/**
 * Service interface for managing JWT and refresh tokens.
 *
 * <p>Provides functionality for generating, validating, and refreshing access tokens, as well as
 * managing refresh tokens.
 */
public interface TokenService {

  /**
   * Generates a JWT access token for a {@link User} based on their user entity.
   *
   * @param user the User entity
   * @return the generated JWT access token as a String
   */
  String generateAccessToken(User user);

  /**
   * Refreshes the access and refresh tokens for a User.
   *
   * <p>Validates the provided refresh token and generates new access and refresh tokens.
   *
   * @param refreshToken the refresh token to validate and refresh
   * @return a {@link TokensResponse} containing the new tokens
   * @throws AuthorizationException if the refresh token is invalid or the associated user is not
   *     found
   */
  TokensResponse refreshTokens(String refreshToken);

  /**
   * Validates the provided JWT access token.
   *
   * @param authToken the JWT token to validate
   * @return true if the token is valid, false otherwise
   */
  boolean isValid(String authToken);

  /**
   * Checks whether the provided JWT token is expired.
   *
   * @param authToken the JWT token to be checked
   * @return {@code true} if the token is expired, {@code false} otherwise
   */
  boolean isExpired(String authToken);

  /**
   * Creates a new refresh token for the specified user ID.
   *
   * @param email the email of the user for whom the refresh token is created
   * @return the created {@link RefreshToken}
   */
  RefreshToken createRefreshToken(String email);

  /**
   * Extracts the username from the provided JWT access token.
   *
   * @param token the JWT token
   * @return the username (email) extracted from the token
   */
  String getUsernameFromToken(String token);

  /**
   * Extracts the userId from the provided JWT access token.
   *
   * @param token the JWT token
   * @return the userId (uuid) extracted from the token
   */
  String getUserIdFromToken(String token);

  /**
   * Extracts the hasActiveSubscription from the provided JWT access token.
   *
   * @param token the JWT token
   * @return the boolean hasActiveSubscription extracted from the token
   */
  Boolean hasActiveSubscriptionFromToken(String token);

  /**
   * Extracts the subscriptionEndDate from the provided JWT access token.
   *
   * @param token the JWT token
   * @return the Optional<LocalDateTime></LocalDateTime> hasActiveSubscription extracted from the
   *     token
   */
  Optional<LocalDateTime> getSubscriptionEndDateFromToken(String token);

  /**
   * Extracts user roles from a JWT token and converts them to granted authorities.
   *
   * @param token the JWT token
   * @return a collection of {@link GrantedAuthority} representing the user's roles
   */
  Collection<? extends GrantedAuthority> getAuthorityFromToken(String token);

  /**
   * Extracts the refresh token from the cookies in the given HTTP request.
   *
   * @param request the {@link HttpServletRequest} containing the cookies
   * @return the value of the "refreshToken" cookie if present, or {@code null} if the cookie is not
   *     found
   */
  String getRefreshTokenFromRequest(HttpServletRequest request);
}
