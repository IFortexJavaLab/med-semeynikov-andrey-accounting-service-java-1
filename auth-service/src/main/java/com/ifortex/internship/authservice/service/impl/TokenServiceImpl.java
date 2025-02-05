package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.exception.AuthServiceException;
import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.model.constant.UserRole;
import com.ifortex.internship.authservice.service.RefreshTokenService;
import com.ifortex.internship.authservice.service.TokenService;
import com.ifortex.internship.authserviceapi.dto.response.TokensResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

  @Value("${app.jwtSecret}")
  private String jwtSecret;

  @Value("${app.jwtExpirationMs}")
  private int jwtExpirationMs;

  private final RefreshTokenService refreshTokenService;
  //private final CookieService cookieService;

  public TokenServiceImpl(RefreshTokenService refreshTokenService) {
    this.refreshTokenService = refreshTokenService;
  }

  public String generateAccessToken(String email, List<String> roles, String userId) {

    return Jwts.builder()
        .subject(email)
        .claim("roles", roles)
        .claim("userId", userId)
        .issuedAt(new Date(System.currentTimeMillis()))
        .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
        .signWith(getSigningKey())
        .compact();
  }

  public TokensResponse refreshTokens(String refreshToken) {
    log.debug("Refreshing access token");

    try {
      RefreshToken storedRefreshtoken = refreshTokenService.findByToken(refreshToken);
      refreshTokenService.verifyExpiration(storedRefreshtoken);

      User user = storedRefreshtoken.getUser();

      List<String> roles =
          user.getRoles().isEmpty()
              ? List.of(UserRole.ROLE_NON_SUBSCRIBED_USER.name())
              : user.getRoles().stream().map(role -> role.getName().name()).toList();

      String newAccessToken = generateAccessToken(user.getEmail(), roles, user.getUserId());
      log.debug("Access token refreshed successfully for user: {}", user.getEmail());

      RefreshToken newRefreshToken = createRefreshToken(user.getEmail());

      /*ResponseCookie accessTokenCookie = cookieService.createAccessTokenCookie(newAccessToken);
      ResponseCookie refreshTokenCookie =
          cookieService.createRefreshTokenCookie(newRefreshToken.getToken());*/
      //return new CookieTokensResponse(accessTokenCookie, refreshTokenCookie);
      
      return new TokensResponse(newAccessToken, newRefreshToken.getToken());
      
    } catch (AuthServiceException e) {
      log.debug("Exception message: {}", e.getMessage());
      throw new AuthorizationException("Your session has expired. Please log in again.");
    }
  }

  public boolean isValid(String authToken) {

    try {
      Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
      log.debug("Access token is valid");
      return true;
    } catch (SignatureException e) {
      log.debug("Invalid JWT signature: {}", e.getMessage());
      throw new AuthorizationException("JWT token is invalid. Please log in again.");
    } catch (MalformedJwtException e) {
      log.debug("Invalid JWT token: {}", e.getMessage());
      throw new AuthorizationException("JWT token is malformed. Please log in again.");
    } catch (UnsupportedJwtException e) {
      log.debug("JWT token is unsupported: {}", e.getMessage());
      throw new AuthorizationException("JWT token is unsupported. Please log in again.");
    } catch (IllegalArgumentException e) {
      log.debug("JWT claims string is empty: {}", e.getMessage());
      throw new AuthorizationException("JWT claims string is empty. Please log in again.");
    } catch (ExpiredJwtException e) {
      log.debug("JWT token is expired: {}", e.getMessage());
    }

    return false;
  }

  public boolean isExpired(String authToken) {

    try {
      Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
      return false;
    } catch (ExpiredJwtException e) {
      log.debug("JWT token is expired: {}", e.getMessage());
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private SecretKey getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
    return Keys.hmacShaKeyFor(keyBytes);
  }

  public RefreshToken createRefreshToken(String email) {
    return refreshTokenService.createRefreshToken(email);
  }

  public String getUsernameFromToken(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload()
        .getSubject();
  }

  public String getUserIdFromToken(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload()
        .get("userId", String.class);
  }

  public Collection<? extends GrantedAuthority> getAuthorityFromToken(String token) {

    log.debug("Getting authorities from access token");

    final Claims claims =
        Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    List<String> roles = claims.get("roles", List.class);

    log.debug("Got roles from token: {}", roles.toString());

    List<SimpleGrantedAuthority> authorities =
        roles.stream().map(SimpleGrantedAuthority::new).toList();

    log.debug("Made authority from roles: {}", authorities);

    return authorities;
  }

  public String getRefreshTokenFromRequest(HttpServletRequest request) {

    return Optional.ofNullable(request.getCookies()).stream()
        .flatMap(Stream::of)
        .filter(cookie -> "refreshToken".equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }
}
