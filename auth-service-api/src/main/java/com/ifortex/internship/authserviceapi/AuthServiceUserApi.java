package com.ifortex.internship.authserviceapi;

import com.ifortex.internship.authserviceapi.config.FeignClientConfiguration;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import feign.FeignException;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "auth-service",
    path = "/api/v1/account",
    configuration = FeignClientConfiguration.class)
public interface AuthServiceUserApi {

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
   * Retrieves a user based on the current authentication from the auth-service.
   *
   * <p>This method calls the auth-service endpoint to fetch a user's data based on the current
   * authentication and returns it as a {@link AuthUserDto}.
   *
   * @return a {@code ResponseEntity} containing the {@code AuthUserDto} with user details, or a
   *     {@code ResponseEntity} with an appropriate HTTP status if the user is not found.
   * @throws FeignException if there is an issue with the communication with the auth-service.
   */
  @GetMapping()
  ResponseEntity<AuthUserDto> getUserByAuthentication(@PathVariable("email") String email);

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
  @GetMapping("/users")
  ResponseEntity<List<AuthUserDto>> getAllUsers();
}
