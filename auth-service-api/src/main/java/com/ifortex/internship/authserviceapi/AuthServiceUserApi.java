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
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
    name = "auth-service",
    path = "/api/v1/auth-service/users",
    configuration = FeignClientConfiguration.class)
public interface AuthServiceUserApi {

  /**
   * Retrieves a list of all users from the auth-service.
   *
   * <p>This method calls the auth-service endpoint to fetch all user data and maps it to a list of
   * {@code AuthUserDto}.
   *
   * @return a {@code ResponseEntity} containing a list of {@code AuthUserDto} objects representing
   *     all users, or an empty list if no users exist.
   * @throws FeignException if there is an issue with the communication with the auth-service.
   */
  @GetMapping()
  ResponseEntity<List<AuthUserDto>> getAllUsers();

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
   * auth-service endpoint. The update is performed based on the provided {@code userId} and
   * {@link TwoFactorAuthRequest}.
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
   * calling the auth-service endpoint. The update is performed based on the provided
   * {@link TwoFactorAuthRequest}.
   *
   * @param request a {@code TwoFactorAuthRequest} object containing the new 2FA setting.
   * @return a ResponseEntity containing the updated {@link AuthUserDto} with the new 2FA status.
   * @throws FeignException if there is an issue with the communication with the auth-service.
   */
  @PatchMapping("user/2fa")
  ResponseEntity<?> changeTwoFactorAuth(@RequestBody TwoFactorAuthRequest request);
}
