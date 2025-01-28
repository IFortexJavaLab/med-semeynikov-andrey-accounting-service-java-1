package com.ifortex.internship.authserviceapi;

import com.ifortex.internship.authserviceapi.config.FeignClientConfiguration;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
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
    path = "/api/v1/users",
    configuration = FeignClientConfiguration.class)
public interface AuthServiceUserApi {

  /**
   * Changes the password for a user.
   *
   * @param request the change password request containing current and new passwords
   * @return SuccessResponse indicating the password change result
   */
  @PatchMapping("/change-password")
  ResponseEntity<SuccessResponse> changePassword(@RequestBody ChangePasswordRequest request);

  /**
   * Retrieves a user by their email address from the auth-service.
   *
   * <p>This method calls the auth-service endpoint to fetch a user's data by email and returns it
   * as a {@link AuthUserDto}.
   *
   * @param email the email address of the user to retrieve.
   * @return a {@code ResponseEntity} containing the {@code AuthUserDto} with user details, or a
   *     {@code ResponseEntity} with an appropriate HTTP status if the user is not found.
   * @throws FeignException if there is an issue with the communication with the auth-service.
   */
  @GetMapping("/{email}")
  ResponseEntity<AuthUserDto> getUserByEmail(@PathVariable("email") String email);

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
}
