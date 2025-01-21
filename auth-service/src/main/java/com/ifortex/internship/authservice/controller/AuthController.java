package com.ifortex.internship.authservice.controller;

import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.TokenService;
import com.ifortex.internship.authserviceapi.dto.request.LoginRequest;
import com.ifortex.internship.authserviceapi.dto.request.PasswordResetRequest;
import com.ifortex.internship.authserviceapi.dto.request.PasswordResetWithOtpDto;
import com.ifortex.internship.authserviceapi.dto.request.RegistrationRequest;
import com.ifortex.internship.authserviceapi.dto.request.VerifyLoginOtpRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.CookieTokensResponse;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final TokenService tokenService;

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody @Valid RegistrationRequest request) {

    log.info("Received registration request for email: {}", request.getEmail());
    SuccessResponse response = authService.registerUser(request);

    return ResponseEntity.ok().body(response.getMessage());
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody @Valid LoginRequest loginRequest) {

    log.info("Login attempt for email: {}", loginRequest.getEmail());
    AuthResponse authResponse = authService.authenticateUser(loginRequest);

    HttpHeaders headers = new HttpHeaders();
    if (authResponse.getCookieTokensResponse() != null) {
      headers.add(
          HttpHeaders.SET_COOKIE,
          authResponse.getCookieTokensResponse().getAccessCookie().toString());
      headers.add(
          HttpHeaders.SET_COOKIE,
          authResponse.getCookieTokensResponse().getRefreshCookie().toString());

      log.debug("Refresh and access tokens set in cookie for email: {}", loginRequest.getEmail());
      log.info("User: {} successfully logged in", loginRequest.getEmail());
    }

    return ResponseEntity.ok().headers(headers).body(authResponse.getMessage());
  }

  @PostMapping("/verify-otp")
  public ResponseEntity<?> completeLoginWithOtp(@RequestBody @Valid VerifyLoginOtpRequest request) {

    log.info("Verify otp attempt to log in for email: {}", request.getEmail());
    AuthResponse authResponse = authService.completeLoginWithOtp(request);

    HttpHeaders headers = new HttpHeaders();
    headers.add(
        HttpHeaders.SET_COOKIE,
        authResponse.getCookieTokensResponse().getAccessCookie().toString());
    headers.add(
        HttpHeaders.SET_COOKIE,
        authResponse.getCookieTokensResponse().getRefreshCookie().toString());

    log.debug("Refresh and access tokens set in cookie for email: {}", request.getEmail());
    log.info("User: {} successfully logged in", request.getEmail());

    return ResponseEntity.ok().headers(headers).body(authResponse.getMessage());
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout() {

    log.info("Logout attempt");

    AuthResponse authResponse = authService.logoutUser();

    HttpHeaders headers = new HttpHeaders();
    headers.add(
        HttpHeaders.SET_COOKIE,
        authResponse.getCookieTokensResponse().getAccessCookie().toString());
    headers.add(
        HttpHeaders.SET_COOKIE,
        authResponse.getCookieTokensResponse().getRefreshCookie().toString());

    log.debug("Clean tokens set in cookie for user: {}", authResponse.getEmail());
    log.info("Logout successful for user: {}", authResponse.getEmail());

    return ResponseEntity.ok().headers(headers).body(authResponse.getMessage());
  }

  @PostMapping("/reset-password/request")
  public ResponseEntity<?> initiatePasswordReset(@RequestBody @Valid PasswordResetRequest request) {

    log.info("Reset password attempt for user: {}", request.getEmail());
    SuccessResponse response = authService.initiatePasswordReset(request);
    log.info("Email with otp to reset password was sent to the email: {}", request.getEmail());

    return ResponseEntity.ok().body(response);
  }

  @PostMapping("/reset-password/confirm")
  public ResponseEntity<?> resetPasswordWithOtp(
      @RequestBody @Valid PasswordResetWithOtpDto request) {

    log.info("Reset password with otp attempt for email: {}", request.getEmail());
    SuccessResponse response = authService.resetPasswordWithOtp(request);

    return ResponseEntity.ok().body(response.getMessage());
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refreshTokens(@CookieValue("refreshToken") String refreshToken) {

    log.info("Tokens refresh attempt.");
    CookieTokensResponse cookie = tokenService.refreshTokens(refreshToken);

    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie.getAccessCookie().toString());
    headers.add(HttpHeaders.SET_COOKIE, cookie.getRefreshCookie().toString());

    log.info("Tokens refreshed successfully.");

    return ResponseEntity.ok().headers(headers).body("Tokens refreshed successfully");
  }
}
