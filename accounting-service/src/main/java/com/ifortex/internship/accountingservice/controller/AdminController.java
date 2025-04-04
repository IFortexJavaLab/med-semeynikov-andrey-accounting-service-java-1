package com.ifortex.internship.accountingservice.controller;

import com.ifortex.internship.accountingservice.dto.request.BlockUserRequest;
import com.ifortex.internship.accountingservice.dto.request.CreateAdminRequest;
import com.ifortex.internship.accountingservice.dto.request.CreateClientRequest;
import com.ifortex.internship.accountingservice.dto.request.CreateParamedicRequest;
import com.ifortex.internship.accountingservice.dto.request.UnblockUserRequest;
import com.ifortex.internship.accountingservice.dto.request.UpdateAccountDto;
import com.ifortex.internship.accountingservice.dto.request.UserSearchRequest;
import com.ifortex.internship.accountingservice.dto.response.CreateUserResponse;
import com.ifortex.internship.accountingservice.dto.response.SuccessResponse;
import com.ifortex.internship.accountingservice.dto.response.TemporaryPasswordResponse;
import com.ifortex.internship.accountingservice.dto.response.UserListViewDto;
import com.ifortex.internship.accountingservice.service.AdminAccountManagementService;
import com.ifortex.internship.accountingservice.service.AdminService;
import com.ifortex.internship.accountingservice.service.AuthService;
import com.ifortex.internship.accountingservice.service.ClientService;
import com.ifortex.internship.accountingservice.service.ParamedicService;
import com.ifortex.internship.accountingapi.dto.response.AccountDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Validated
@Slf4j
@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Admin functions API")
public class AdminController {

    AuthService authService;
    AdminAccountManagementService adminAccountManagement;
    ClientService clientService;
    AdminService adminService;
    ParamedicService paramedicService;

    @Operation(summary = "Update user profile", description = "Allows admins to update user information.")
    @PatchMapping("{accountId}")
    public ResponseEntity<AccountDto> updateProfile(
        @PathVariable("accountId") UUID accountId,
        @RequestBody UpdateAccountDto updateAccountDto) {
        log.info("Attempt to update account with ID: {}", accountId);
        AccountDto updatedUser = adminAccountManagement.updateAccountByAdmin(accountId, updateAccountDto);
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Get user's profile by ID")
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDto> getUserProfileById(@PathVariable UUID accountId) {
        log.info("Attempt to get account profile by ID: {}", accountId);
        var fullUser = adminAccountManagement.getUserProfileById(accountId);
        return ResponseEntity.ok(fullUser);
    }

    @Operation(
        summary = "Search users",
        description = "Allows admins to search users with filters.")
    @PostMapping("/search")
    public ResponseEntity<List<UserListViewDto>> searchUsers(
        @RequestBody UserSearchRequest request,
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page can't be a negative number")
        int page,
        @RequestParam(defaultValue = "20")
        @Min(value = 1, message = "Size of the page can't be less than 1")
        @Max(value = 100, message = "Size of the page can't be more than 100")
        int size
    ) {
        log.info("Request to search with parameters through users");
        Page<UserListViewDto> result = adminAccountManagement.searchAccounts(request, page, size);
        return ResponseEntity.ok(result.getContent());
    }

    @Operation(summary = "Creates new admin")
    @PostMapping("/admin")
    public ResponseEntity<CreateUserResponse> createAdmin(@RequestBody @Valid CreateAdminRequest request) {
        log.info("Attempt to register Admin with email: {}", request.getEmail());
        CreateUserResponse response = adminService.createAdmin(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Creates new client")
    @PostMapping("/client")
    public ResponseEntity<CreateUserResponse> createClient(@RequestBody @Valid CreateClientRequest request) {
        CreateUserResponse response = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Creates new paramedic")
    @PostMapping("/medic")
    public ResponseEntity<CreateUserResponse> createParamedic(@RequestBody @Valid CreateParamedicRequest request) {
        CreateUserResponse response = paramedicService.createParamedic(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Reset user's password by generating temp password")
    @PostMapping("/users/{userId}/reset-password-temp")
    public ResponseEntity<TemporaryPasswordResponse> resetPasswordWithTemp(@PathVariable("userId") UUID accountId) {
        log.info("Attempt to reset password for account: {} by admin", accountId);
        TemporaryPasswordResponse response = authService.resetPasswordWithTemp(accountId);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Reset user's password by deleting current password",
        description =
            "Reset user's password by deleting current password and sending email to user with request to reset password")
    @PostMapping("/users/{accountId}/reset-password-email")
    public ResponseEntity<SuccessResponse> resetPasswordWithEmail(@PathVariable("accountId") UUID accountId) {
        log.info("Attempt to reset password for account: {} by admin", accountId);
        SuccessResponse response = authService.resetPasswordWithEmail(accountId);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Block user",
        description =
            "Blocks a user until a specified date. Only ADMIN and SUPER_ADMIN can perform this action")
    @PatchMapping("/users/block")
    public ResponseEntity<Void> blockUser(@RequestBody @Valid BlockUserRequest request) {
        log.info("Attempt to block user with account: {}", request.getAccountId());
        adminAccountManagement.blockAccount(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Unblock user",
        description =
            "Unblocks a previously blocked user. Only ADMIN and SUPER_ADMIN can perform this action")
    @PatchMapping("/users/unblock")
    public ResponseEntity<Void> unblockUser(@RequestBody @Valid UnblockUserRequest request) {
        adminAccountManagement.unblockAccount(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Soft delete user",
        description =
            "Soft deletes a user by marking them as deleted without removing their data from the system")
    @DeleteMapping("/users/{accountId}")
    public ResponseEntity<Void> softDeleteUser(@PathVariable("accountId") UUID accountId) {
        log.info("Attempt to delete account: {} with soft delete", accountId);
        adminAccountManagement.softDelete(accountId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete user entirely")
    @DeleteMapping("/users/{accountId}/hard")
    @PreAuthorize("@roleSecurity.isSuperAdmin(authentication)")
    public ResponseEntity<Void> hardDeleteUser(@PathVariable("accountId") UUID accountId) {
        log.info("Attempt to delete account: {} with hard delete", accountId);
        adminAccountManagement.hardDelete(accountId);
        return ResponseEntity.noContent().build();
    }
}
