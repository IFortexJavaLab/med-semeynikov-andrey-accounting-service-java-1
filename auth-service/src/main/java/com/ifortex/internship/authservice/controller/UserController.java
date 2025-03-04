package com.ifortex.internship.authservice.controller;

import com.ifortex.internship.authservice.model.UserDetailsImpl;
import com.ifortex.internship.authservice.service.AccountService;
import com.ifortex.internship.authservice.service.AuthService;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.request.PasswordResetWithOtpDto;
import com.ifortex.internship.authserviceapi.dto.request.UpdateUserDto;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.ChangeEmailResponse;
import com.ifortex.internship.authserviceapi.dto.response.ClientDto;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User Account", description = "User account management")
@SecurityRequirement(name = "BearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final AccountService userService;
    private final AuthService authService;
    private final AccountService accountService;

    @Operation(summary = "Update user", description = "Allows updating user information.")
    @PatchMapping
    public ResponseEntity<ClientDto> updateUser(@RequestBody @Valid UpdateUserDto updateUserDto) {
        var updatedUser = userService.updateUserByAuthentication(updateUserDto);
        return ResponseEntity.ok().body(updatedUser);
    }

    @Operation(
        summary = "Get current user profile",
        description = "Retrieve detailed user information by authentication.")
    @GetMapping()
    public ResponseEntity<ClientDto> getUserByAuth() {
        return ResponseEntity.ok(userService.getUserProfileByAuthentication());
    }

    @Operation(summary = "Change password")
    @PatchMapping("/password")
    public ResponseEntity<SuccessResponse> changePassword(@RequestBody @Valid ChangePasswordRequest request) {

        String userEmail =
            ((UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .getEmail();
        log.info("Attempt to change password for user: {}", userEmail);
        AuthResponse response = userService.changePassword(request, userEmail);

        log.info("Logout successful for user: {}", userEmail);

        return ResponseEntity.ok().body(new SuccessResponse(response.getMessage(), response.getLink()));
    }

    @PostMapping("/email")
    @Operation(summary = "Change email")
    public ResponseEntity<ChangeEmailResponse> changeEmailRequest(
        @RequestParam("newEmail")
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email cannot be empty")
        String newEmail) {

        var userId = authService.getAccountIdFromAuthentication();
        log.info("Request to change email for user with ID: {}", userId);
        ChangeEmailResponse response = userService.changeEmailRequest(newEmail);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/email-confirm")
    @Operation(summary = "Change email")
    public ResponseEntity<ChangeEmailResponse> changeEmailConfirm(
        @RequestParam("newEmail")
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email cannot be empty")
        String newEmail,
        @RequestParam(name = "code")
        @Pattern(
            regexp = "^\\d{6}$",
            message = "One-time password must consist of exactly 6 digits")
        String code) {
        var userId = authService.getAccountIdFromAuthentication();
        log.info("Request to confirm changing email for user with ID: {}", userId);
        ChangeEmailResponse response = userService.changeEmailConfirm(newEmail, code);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Request password reset")
    @PostMapping("/password/reset")
    public ResponseEntity<SuccessResponse> initiatePasswordReset(
        @RequestParam("email")
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email cannot be empty")
        String email) {

        log.info("Reset password attempt for user: {}", email);
        SuccessResponse response = accountService.passwordResetRequest(email);
        log.info("Email with otp to reset password was sent to the email: {}", email);

        return ResponseEntity.ok().body(response);
    }

    @Operation(summary = "Reset password with OTP")
    @PostMapping("/password/reset-confirm")
    public ResponseEntity<SuccessResponse> resetPasswordWithOtp(
        @RequestBody @Valid PasswordResetWithOtpDto request) {

        log.info("Reset password with otp attempt for email: {}", request.getEmail());
        SuccessResponse response = accountService.resetPasswordConfirm(request);

        return ResponseEntity.ok().body(response);
    }
}
