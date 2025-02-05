package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.service.CookieService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class CookieServiceImpl implements CookieService {

  @Value("${app.jwtExpirationMs}")
  private int jwtExpirationMs;

  @Value("${app.refreshTokenExpirationS}")
  private int refreshTokenDurationS;

  public ResponseCookie createRefreshTokenCookie(String refreshToken) {
    return ResponseCookie.from("refreshToken", refreshToken)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(refreshTokenDurationS)
        .sameSite("Strict")
        .build();
  }

  public ResponseCookie deleteRefreshTokenCookie() {
    return ResponseCookie.from("refreshToken", "")
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(0)
        .sameSite("Strict")
        .build();
  }

  public ResponseCookie createAccessTokenCookie(String accessToken) {
    return ResponseCookie.from("accessToken", accessToken)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(jwtExpirationMs / 1000)
        .sameSite("Strict")
        .build();
  }

  public ResponseCookie deleteAccessTokenCookie() {
    return ResponseCookie.from("accessToken", "")
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(0) // Удаляем cookie
        .sameSite("Strict")
        .build();
  }
}
