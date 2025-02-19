package com.ifortex.internship.authservice.controller;

import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authserviceapi.dto.request.CreateAdminRequest;
import com.ifortex.internship.authserviceapi.dto.request.CreateUserRequest;
import com.ifortex.internship.authserviceapi.dto.response.CreateUserResponse;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import com.ifortex.internship.authserviceapi.dto.response.TemporaryPasswordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounting/")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Admin functions API")
public class AdminController {

  private final AuthService authService;

  @Operation(summary = "Creates new admin")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @PostMapping("/admin")
  public ResponseEntity<?> createAdmin(@RequestBody CreateAdminRequest request) {

    CreateUserResponse response = authService.createAdmin(request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "Creates new client")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @PostMapping("/client")
  public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {

    CreateUserResponse response = authService.createUser(request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "Reset user's password by generating temp password")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @PostMapping("/users/{userId}/reset-password-temp")
  public ResponseEntity<?> resetPasswordWithTemp(@PathVariable("userId") String userId) {

    TemporaryPasswordResponse response = authService.resetPasswordWithTemp(userId);

    return ResponseEntity.ok(response);
  }

  @Operation(
      summary = "Reset user's password by deleting current password",
      description =
          "Reset user's password by deleting current password and sending email to user with request to reset password")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @PostMapping("/users/{userId}/reset-password-email")
  public ResponseEntity<?> resetPasswordWithEmail(@PathVariable("userId") String userId) {

    SuccessResponse response = authService.resetPasswordWithEmail(userId);

    return ResponseEntity.ok(response);
  }
}
