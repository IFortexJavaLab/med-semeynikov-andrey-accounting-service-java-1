package com.ifortex.internship.authservice.controller;

import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.TokenService;
import com.ifortex.internship.authserviceapi.dto.request.LoginRequest;
import com.ifortex.internship.authserviceapi.dto.request.RegistrationRequest;
import com.ifortex.internship.authserviceapi.dto.request.VerifyLoginOtpRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import com.ifortex.internship.authserviceapi.dto.response.TokensResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User account authentication")
public class AuthController {

  private final AuthService authService;
  private final TokenService tokenService;

  @Operation(summary = "User registration", description = "Registers a new user in the system.")
  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody @Valid RegistrationRequest request) {
    log.info("Received registration request for email: {}", request.getEmail());
    SuccessResponse response = authService.registerUser(request);
    return ResponseEntity.ok().body(response.getMessage());
  }

  @Operation(
      summary = "User login",
      description = "Authenticates a user and returns access & refresh tokens in cookies.")
  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody @Valid LoginRequest loginRequest) {
    log.info("Login attempt for email: {}", loginRequest.getEmail());
    AuthResponse authResponse = authService.authenticateUser(loginRequest);

    /*HttpHeaders headers = new HttpHeaders();
    if (authResponse.getCookieTokensResponse() != null) {
      headers.add(HttpHeaders.SET_COOKIE, authResponse.getCookieTokensResponse().getAccessCookie().toString());
      headers.add(HttpHeaders.SET_COOKIE, authResponse.getCookieTokensResponse().getRefreshCookie().toString());

      log.debug("Refresh and access tokens set in cookie for email: {}", loginRequest.getEmail());*/

    return ResponseEntity.ok(authResponse);
  }

  @Operation(
      summary = "Verify login with OTP",
      description = "Completes the login process using a One-Time Password (OTP).")
  @PostMapping("/login/verify-otp")
  public ResponseEntity<?> completeLoginWithOtp(@RequestBody @Valid VerifyLoginOtpRequest request) {
    log.info("Verify otp attempt to log in for email: {}", request.getEmail());
    AuthResponse authResponse = authService.completeLoginWithOtp(request);

    /* HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, authResponse.getCookieTokensResponse().getAccessCookie().toString());
    headers.add(HttpHeaders.SET_COOKIE, authResponse.getCookieTokensResponse().getRefreshCookie().toString());

    log.debug("Refresh and access tokens set in cookie for email: {}", request.getEmail());*/
    log.info("User: {} successfully logged in", request.getEmail());

    return ResponseEntity.ok(authResponse.getTokens());
  }

  @Operation(
      summary = "User logout",
      description = "Logs out the user by invalidating tokens and clearing authentication cookies.")
  @PostMapping("/logout")
  public void logout() {
    log.info("Logout attempt");

    AuthResponse authResponse = authService.logoutUser();

    /* HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, authResponse.getCookieTokensResponse().getAccessCookie().toString());
    headers.add(HttpHeaders.SET_COOKIE, authResponse.getCookieTokensResponse().getRefreshCookie().toString());*/

    // log.debug("Clean tokens set in cookie for user: {}", authResponse.getEmail());
    log.info("Logout successful for user: {}", authResponse.getEmail());
  }

  @Operation(
      summary = "Refresh access token",
      description = "Refreshes the access token using a valid refresh token")
  @PostMapping("/refresh-token")
  public ResponseEntity<?> refreshTokens(@RequestParam("refreshToken") String refreshToken) {
    log.info("Tokens refresh attempt.");
    TokensResponse tokens = tokenService.refreshTokens(refreshToken);

    /* HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie.getAccessCookie().toString());
    headers.add(HttpHeaders.SET_COOKIE, cookie.getRefreshCookie().toString());*/

    log.info("Tokens refreshed successfully.");

    return ResponseEntity.ok().body(tokens);
  }
}
