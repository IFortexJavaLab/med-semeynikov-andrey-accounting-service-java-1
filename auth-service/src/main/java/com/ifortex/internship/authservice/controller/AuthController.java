package com.ifortex.internship.authservice.controller;

import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.ClientService;
import com.ifortex.internship.authservice.service.TokenService;
import com.ifortex.internship.authserviceapi.dto.request.LoginRequest;
import com.ifortex.internship.authserviceapi.dto.request.RegistrationRequest;
import com.ifortex.internship.authserviceapi.dto.request.VerifyLoginOtpRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.TokensResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
    private final ClientService clientService;

    @Operation(summary = "User registration", description = "Registers a new user in the system.")
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody @Valid RegistrationRequest request) {
        log.info("Received registration request for email: {}", request.getEmail());
        clientService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(
        summary = "User login",
        description = "Authenticates a user and returns access & refresh tokens.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest loginRequest) {

        log.info("Login attempt for email: {}", loginRequest.getEmail());
        AuthResponse authResponse = authService.authenticateUser(loginRequest);

        return ResponseEntity.ok(authResponse);
    }

    @Operation(
        summary = "Verify login with OTP",
        description = "Completes the login process using a One-Time Password (OTP).")
    @PostMapping("/login/verify-otp")
    public ResponseEntity<TokensResponse> completeLoginWithOtp(@RequestBody @Valid VerifyLoginOtpRequest request) {

        log.info("Verify otp attempt to log in for email: {}", request.getEmail());
        AuthResponse authResponse = authService.completeLoginWithOtp(request);

        log.info("User: {} successfully logged in", request.getEmail());

        return ResponseEntity.ok(authResponse.getTokens());
    }

    @Operation(summary = "User logout", description = "Logs out the user by deleting refresh token.")
    @PostMapping("/logout")
    public void logout() {
        authService.logoutUser();
    }

    @Operation(
        summary = "Refresh access token",
        description = "Refreshes the access token using a valid refresh token")
    @PostMapping("/refresh-token")
    public ResponseEntity<TokensResponse> refreshTokens(@RequestParam("refreshToken") String refreshToken) {

        log.info("Tokens refresh attempt.");

        TokensResponse tokens = tokenService.refreshTokens(refreshToken);
        log.info("Tokens refreshed successfully.");

        return ResponseEntity.ok().body(tokens);
    }
}
