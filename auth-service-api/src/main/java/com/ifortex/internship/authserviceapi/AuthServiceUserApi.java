package com.ifortex.internship.authserviceapi;

import com.ifortex.internship.authserviceapi.config.FeignClientConfiguration;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.TwoFactorAuthRequest;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
    name = "auth-service",
    path = "/api/v1/auth-service/users",
    configuration = FeignClientConfiguration.class)
public interface AuthServiceUserApi {

  /**
   * Retrieves a user by their userId from the auth-service.
   *
   * <p>This method calls the auth-service endpoint to fetch a user's data by userId and returns it
   * as a {@link AuthUserDto}.
   *
   * @param userId the userId of the user to retrieve.
   * @return a {@code ResponseEntity} containing the {@code AuthUserDto} with user details, or a
   *     {@code ResponseEntity} with an appropriate HTTP status if the user is not found.
   * @throws FeignException if there is an issue with the communication with the auth-service.
   */
  @GetMapping("/{userId}")
  ResponseEntity<AuthUserDto> getUserById(@PathVariable("userId") String userId);

  /**
   * Retrieves a user based on the current authentication from the auth-service.
   *
   * <p>This method calls the auth-service endpoint to fetch a user's data based on the current
   * authentication and returns it as a {@link AuthUserDto}.
   *
   * @return a {@code ResponseEntity} containing the {@code AuthUserDto} with user details, or a
   *     {@code ResponseEntity} with an appropriate HTTP status if the user is not found.
   * @throws FeignException if there is an issue with the communication with the auth-service.
   */
  @GetMapping("/user")
  ResponseEntity<AuthUserDto> getUserByAuthentication();

  /**
   * Updates the two-factor authentication (2FA) setting for a specific user as an administrator.
   *
   * <p>This method allows an administrator to enable or disable 2FA for a user by calling the
   * auth-service endpoint. The update is performed based on the provided {@code userId} and {@link
   * TwoFactorAuthRequest}.
   *
   * @param userId the unique identifier of the user whose 2FA setting needs to be changed.
   * @param request a {@code TwoFactorAuthRequest} object containing the new 2FA setting.
   * @return a ResponseEntity containing the updated {@link AuthUserDto} with the new 2FA status.
   * @throws FeignException if there is an issue with the communication with the auth-service.
   */
  @PatchMapping("/{userId}/2fa")
  ResponseEntity<AuthUserDto> changeTwoFactorAuthByAdmin(
      @PathVariable("userId") String userId, @RequestBody TwoFactorAuthRequest request);

  /**
   * Updates the two-factor authentication (2FA) setting for the currently authenticated user.
   *
   * <p>This method allows an authenticated user to enable or disable their own 2FA setting by
   * calling the auth-service endpoint. The update is performed based on the provided {@link
   * TwoFactorAuthRequest}.
   *
   * @param request a {@code TwoFactorAuthRequest} object containing the new 2FA setting.
   * @return a ResponseEntity containing the updated {@link AuthUserDto} with the new 2FA status.
   * @throws FeignException if there is an issue with the communication with the auth-service.
   */
  @PatchMapping("user/2fa")
  ResponseEntity<AuthUserDto> changeTwoFactorAuth(@RequestBody TwoFactorAuthRequest request);

  /**
   * Method for searching user details in the authentication service.
   *
   * <p>This method retrieves user information based on provided filters such as user IDs, roles,
   * status, and email. It supports optional filters for roles, status, and email, while the list of
   * user IDs is mandatory.
   *
   * @param userIds List of user IDs to retrieve details for. Cannot be {@code null}.
   * @param roles (Optional) List of roles to filter users by (e.g., "ADMIN", "USER").
   * @param status (Optional) User status to filter by (e.g., "ACTIVE", "BLOCKED").
   * @param email (Optional) Email address to filter users by. Supports partial, case-insensitive
   *     matching.
   * @return A list of {@link AuthUserDto} containing user details that match the provided filters.
   */
  @PostMapping("/search")
  List<AuthUserDto> searchUsers(
      @RequestBody List<String> userIds,
      @RequestParam(value = "roles", required = false) List<String> roles,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "email", required = false) String email);
}
