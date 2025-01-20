package com.ifortex.internship.authservice.service;

import org.springframework.http.ResponseCookie;

/**
 * Interface for managing HTTP cookies related to authentication tokens.
 *
 * <p>Provides methods for creating and deleting cookies for refresh and access tokens, with
 * security settings like HttpOnly, Secure, and SameSite attributes.
 */
public interface CookieService {

  /**
   * Creates an HTTP cookie for storing a refresh token.
   *
   * @param refreshToken the refresh token to store in the cookie
   * @return a {@link ResponseCookie} configured for the refresh token
   */
  ResponseCookie createRefreshTokenCookie(String refreshToken);

  /**
   * Deletes the HTTP cookie used for storing a refresh token.
   *
   * @return a ResponseCookie configured to delete the refresh token cookie
   */
  ResponseCookie deleteRefreshTokenCookie();

  /**
   * Creates an HTTP cookie for storing an access token.
   *
   * @param accessToken the access token to store in the cookie
   * @return a ResponseCookie configured for the access token
   */
  ResponseCookie createAccessTokenCookie(String accessToken);

  /**
   * Deletes the HTTP cookie used for storing an access token.
   *
   * @return a ResponseCookie configured to delete the access token cookie
   */
  ResponseCookie deleteAccessTokenCookie();
}
