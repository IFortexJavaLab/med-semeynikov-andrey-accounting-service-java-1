package com.ifortex.internship.authservice.controller;

import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.UserService;
import com.ifortex.internship.authserviceapi.dto.request.BlockUserRequest;
import com.ifortex.internship.authserviceapi.dto.request.CreateAdminRequest;
import com.ifortex.internship.authserviceapi.dto.request.CreateClientRequest;
import com.ifortex.internship.authserviceapi.dto.request.UnblockUserRequest;
import com.ifortex.internship.authserviceapi.dto.response.CreateUserResponse;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import com.ifortex.internship.authserviceapi.dto.response.TemporaryPasswordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Admin functions API")
public class AdminController {

  private final AuthService authService;
  private final UserService userService;

  @Operation(summary = "Creates new admin")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @PostMapping("/admin")
  public ResponseEntity<?> createAdmin(@RequestBody @Valid CreateAdminRequest request) {

    CreateUserResponse response = authService.createAdmin(request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "Creates new client")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @PostMapping("/client")
  public ResponseEntity<?> createClient(@RequestBody @Valid CreateClientRequest request) {

    CreateUserResponse response = authService.createClient(request);

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

  @Operation(
      summary = "Block user",
      description =
          "Blocks a user until a specified date. Only ADMIN and SUPER_ADMIN can perform this action")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @PatchMapping("/users/block")
  public ResponseEntity<Void> blockUser(@RequestBody @Valid BlockUserRequest request) {
    userService.blockUser(request);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Unblock user",
      description =
          "Unblocks a previously blocked user. Only ADMIN and SUPER_ADMIN can perform this action")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @PatchMapping("/users/unblock")
  public ResponseEntity<Void> unblockUser(@RequestBody @Valid UnblockUserRequest request) {
    userService.unblockUser(request);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Soft delete user",
      description =
          "Soft deletes a user by marking them as deleted without removing their data from the system")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @DeleteMapping("/users/{userId}")
  public ResponseEntity<Void> softDeleteUser(@PathVariable("userId") String userId) {
    userService.softDeleteUser(userId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Delete user entirely")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
  @DeleteMapping("/users/{userId}/hard")
  public ResponseEntity<Void> hardDeleteUser(@PathVariable("userId") String userId) {
    userService.hardDelete(userId);
    return ResponseEntity.noContent().build();
  }
}
