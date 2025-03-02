package com.ifortex.internship.authservice.controller;

import com.ifortex.internship.authservice.service.impl.AuthServiceImpl;
import com.ifortex.internship.authservice.service.impl.UserServiceImpl;
import com.ifortex.internship.authserviceapi.dto.request.BlockUserRequest;
import com.ifortex.internship.authserviceapi.dto.request.CreateAdminRequest;
import com.ifortex.internship.authserviceapi.dto.request.CreateClientRequest;
import com.ifortex.internship.authserviceapi.dto.request.UnblockUserRequest;
import com.ifortex.internship.authserviceapi.dto.request.UpdateUserDto;
import com.ifortex.internship.authserviceapi.dto.request.UserSearchRequest;
import com.ifortex.internship.authserviceapi.dto.response.ClientDto;
import com.ifortex.internship.authserviceapi.dto.response.CreateUserResponse;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import com.ifortex.internship.authserviceapi.dto.response.TemporaryPasswordResponse;
import com.ifortex.internship.authserviceapi.dto.response.UserListViewDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@Validated
@Slf4j
@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Admin functions API")
public class AdminController {

    private final AuthServiceImpl authService;
    private final UserServiceImpl userService;

    @Operation(summary = "Update user", description = "Allows admins to update user information.")
    @PatchMapping("{userId}")
    public ResponseEntity<ClientDto> updateUser(
        @PathVariable("userId") String userId,
        @RequestBody UpdateUserDto updateUserDto) {

        log.info("Attempt to update user with ID: {}", userId);
        ClientDto updatedUser = userService.updateUserByAdmin(userId, updateUserDto);
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Get user's profile by ID")
    @GetMapping("/{userId}")
    public ResponseEntity<ClientDto> getUserProfileById(@PathVariable String userId) {

        log.info("Attempt to get user profile by ID: {}", userId);
        var fullUser = userService.getUserProfileById(userId);
        return ResponseEntity.ok(fullUser);
    }

    @Operation(
        summary = "Search users",
        description = "Allows admins to search users with filters and pagination.")
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

        log.info("Request to search with through users");
        Page<UserListViewDto> result = userService.searchUsers(request, page, size);
        return ResponseEntity.ok(result.getContent());
    }

    @Operation(summary = "Creates new admin")
    @PostMapping("/admin")
    public ResponseEntity<?> createAdmin(@RequestBody @Valid CreateAdminRequest request) {

        CreateUserResponse response = authService.createAdmin(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Creates new client")
    @PostMapping("/client")
    public ResponseEntity<?> createClient(@RequestBody @Valid CreateClientRequest request) {

        CreateUserResponse response = authService.createClient(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Reset user's password by generating temp password")
    @PostMapping("/users/{userId}/reset-password-temp")
    public ResponseEntity<?> resetPasswordWithTemp(@PathVariable("userId") String userId) {

        TemporaryPasswordResponse response = authService.resetPasswordWithTemp(userId);

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Reset user's password by deleting current password",
        description =
            "Reset user's password by deleting current password and sending email to user with request to reset password")
    @PostMapping("/users/{userId}/reset-password-email")
    public ResponseEntity<?> resetPasswordWithEmail(@PathVariable("userId") String userId) {

        SuccessResponse response = authService.resetPasswordWithEmail(userId);

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Block user",
        description =
            "Blocks a user until a specified date. Only ADMIN and SUPER_ADMIN can perform this action")
    @PatchMapping("/users/block")
    public ResponseEntity<Void> blockUser(@RequestBody @Valid BlockUserRequest request) {
        userService.blockUser(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Unblock user",
        description =
            "Unblocks a previously blocked user. Only ADMIN and SUPER_ADMIN can perform this action")
    @PatchMapping("/users/unblock")
    public ResponseEntity<Void> unblockUser(@RequestBody @Valid UnblockUserRequest request) {
        userService.unblockUser(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Soft delete user",
        description =
            "Soft deletes a user by marking them as deleted without removing their data from the system")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> softDeleteUser(@PathVariable("userId") String userId) {
        userService.softDeleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete user entirely")
    @DeleteMapping("/users/{userId}/hard")
    public ResponseEntity<Void> hardDeleteUser(@PathVariable("userId") String userId) {
        userService.hardDelete(userId);
        return ResponseEntity.noContent().build();
    }
}
