package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authserviceapi.dto.response.CookieTokensResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

/**
 * Service interface for managing JWT and refresh tokens.
 *
 * <p>Provides functionality for generating, validating, and refreshing access tokens, as well as
 * managing refresh tokens and their associated cookies.
 */
public interface TokenService {

  /**
   * Generates a JWT access token for a {@link User} based on their email and roles.
   *
   * @param email the email of the User
   * @param roles the list of roles assigned to the User
   * @return the generated JWT access token as a String
   */
  String generateAccessToken(String email, List<String> roles);

  /**
   * Refreshes the access and refresh tokens for a User.
   *
   * <p>Validates the provided refresh token and generates new access and refresh tokens. Also
   * creates HTTP cookies for storing the new tokens.
   *
   * @param refreshToken the refresh token to validate and refresh
   * @return a {@link CookieTokensResponse} containing the new tokens as cookies
   * @throws AuthorizationException if the refresh token is invalid or the associated user is not
   *     found
   */
  CookieTokensResponse refreshTokens(String refreshToken);

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
   * @param userId the ID of the user for whom the refresh token is created
   * @return the created {@link RefreshToken}
   */
  RefreshToken createRefreshToken(Long userId);

  /**
   * Extracts the username from the provided JWT access token.
   *
   * @param token the JWT token
   * @return the username (email) extracted from the token
   */
  String getUsernameFromToken(String token);

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
