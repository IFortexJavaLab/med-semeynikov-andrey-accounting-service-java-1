package com.ifortex.internship.authservice.controller;

import com.ifortex.internship.authservice.model.UserDetailsImpl;
import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.UserService;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.request.PasswordResetRequest;
import com.ifortex.internship.authserviceapi.dto.request.PasswordResetWithOtpDto;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "User Account", description = "User account management")
public class UserController {

  private final UserService userService;
  private final AuthService authService;

  @Operation(summary = "Change password")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Password updated successfully"),
          @ApiResponse(responseCode = "400", description = "Invalid request"),
          @ApiResponse(responseCode = "401", description = "Unauthorized")
  })
  @PatchMapping("/password")
  public ResponseEntity<SuccessResponse> changePassword(
      @RequestBody ChangePasswordRequest request) {

    String userEmail =
        ((UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
            .getEmail();
    log.info("Attempt to change password for user: {}", userEmail);
    AuthResponse response = userService.changePassword(request, userEmail);

    HttpHeaders headers = new HttpHeaders();
    headers.add(
        HttpHeaders.SET_COOKIE, response.getCookieTokensResponse().getAccessCookie().toString());
    headers.add(
        HttpHeaders.SET_COOKIE, response.getCookieTokensResponse().getRefreshCookie().toString());

    log.info("Logout successful for user: {}", userEmail);

    return ResponseEntity.ok().headers(headers).body(new SuccessResponse(response.getMessage()));
  }

  @Operation(summary = "Request password reset")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "OTP email sent"),
          @ApiResponse(responseCode = "400", description = "Invalid request")
  })
  @PostMapping("/password/reset")
    public ResponseEntity<?> initiatePasswordReset(@RequestBody @Valid PasswordResetRequest request) {

    log.info("Reset password attempt for user: {}", request.getEmail());
    SuccessResponse response = authService.initiatePasswordReset(request);
    log.info("Email with otp to reset password was sent to the email: {}", request.getEmail());

    return ResponseEntity.ok().body(response);
  }

  @Operation(summary = "Reset password with OTP")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Password reset successful"),
          @ApiResponse(responseCode = "400", description = "Invalid OTP or email")
  })
  @PostMapping("/password/reset-confirm")
  public ResponseEntity<?> resetPasswordWithOtp(
      @RequestBody @Valid PasswordResetWithOtpDto request) {

    log.info("Reset password with otp attempt for email: {}", request.getEmail());
    SuccessResponse response = authService.resetPasswordWithOtp(request);

    return ResponseEntity.ok().body(response.getMessage());
  }

  @Operation(summary = "Get authenticated user")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "User data retrieved"),
          @ApiResponse(responseCode = "401", description = "Unauthorized")
  })
  @GetMapping()
  public ResponseEntity<AuthUserDto> getUserByAuthentication() {
    log.debug("Attempt to get user using jwt");
    AuthUserDto user = userService.getUserByAuthentication();
    return ResponseEntity.ok(user);
  }

  @Operation(summary = "Get user by userId")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "User found"),
          @ApiResponse(responseCode = "404", description = "User not found")
  })
  @GetMapping("/{userId}")
  public ResponseEntity<AuthUserDto> getUserByUserId(@PathVariable String userId) {
    log.debug("Attempt to get user by userId: {}", userId);
    AuthUserDto user = userService.getUserDtoByUserId(userId);
    return ResponseEntity.ok(user);
  }

  @Operation(summary = "Get all users")
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "Users retrieved"),
          @ApiResponse(responseCode = "403", description = "Forbidden")
  })
  @GetMapping("/users")
  public ResponseEntity<List<AuthUserDto>> getAllUsers() {
    log.debug("Attempt to get all users");
    List<AuthUserDto> users = userService.getAllUsers();

    return ResponseEntity.ok(users);
  }
}
