package com.ifortex.internship.authservice.controller;

import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authservice.service.UserService;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.TwoFactorAuthRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// feature refactor to interface, implements there and in the authServiceUserApi (feign)
@Slf4j
@RestController
@RequestMapping("/api/v1/auth-service/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Auth service API")
public class ServiceController {

  private final UserService userService;
  private final AuthService authService;

  @Operation(summary = "Get user by userId")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @GetMapping("/{userId}")
  public ResponseEntity<AuthUserDto> getUserByUserIdByAdmin(@PathVariable String userId) {
    log.debug("Attempt to get user by userId: {}", userId);
    AuthUserDto user = userService.getUserByUserId(userId);
    return ResponseEntity.ok(user);
  }

  @Operation(summary = "Get authenticated user")
  @GetMapping("/user")
  public ResponseEntity<AuthUserDto> getUser() {
    log.debug("Attempt to get user using jwt");
    AuthUserDto user = userService.getUser();
    return ResponseEntity.ok(user);
  }

  @Operation(summary = "Change 2FA by admin")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @PatchMapping("/{userId}/2fa")
  public ResponseEntity<AuthUserDto> changeTwoFactorAuthByAdmin(
      @PathVariable("userId") String userId, @RequestBody TwoFactorAuthRequest request) {
    AuthUserDto authUserDto = userService.changeTwoFactorAuthByAdmin(userId, request);
    return ResponseEntity.ok(authUserDto);
  }

  @Operation(summary = "Change 2FA by authenticated user")
  @PatchMapping("user/2fa")
  public ResponseEntity<?> changeTwoFactorAuth(@RequestBody TwoFactorAuthRequest request) {
    AuthUserDto authUserDto = userService.changeTwoFactorAuth(request);
    return ResponseEntity.ok(authUserDto);
  }

  @Operation(
      summary = "Get User Details",
      description =
          "Retrieve user details based on user IDs, roles, status, and email. Only accessible by ADMIN or SUPER_ADMIN.")
  @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
  @PostMapping("/search")
  List<AuthUserDto> searchUsers(
      @RequestBody List<String> userIds,
      @RequestParam(required = false) List<String> roles,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String email) {
    return authService.searchUsers(userIds, roles, status, email);
  }
}
