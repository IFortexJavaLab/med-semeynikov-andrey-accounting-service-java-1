package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.InvalidRequestException;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import java.util.List;

/**
 * Service interface for managing user-related operations.
 *
 * <p>Provides methods for finding users by their unique identifiers or email addresses, as well as
 * updating sensitive information such as passwords.
 */
public interface UserService {

  /**
   * Finds a user by their unique identifier (ID).
   *
   * @param id the unique identifier of the user
   * @return the {@link User} corresponding to the provided ID
   * @throws EntityNotFoundException if a user with the specified ID is not found
   */
  User findUserById(Long id);

  /**
   * Finds a user by their email address.
   *
   * @param email the email address of the user
   * @return the User corresponding to the provided email
   * @throws EntityNotFoundException if a user with the specified email is not found
   */
  User findUserByEmail(String email);

  /**
   * Changes the password of a user.
   *
   * <p>Verifies the current password provided by the user, ensures that the new password meets the
   * necessary requirements, and updates the password if all validations pass.
   *
   * @param request the request containing the current password, new password, and password
   *     confirmation
   * @param userEmail the email of the user whose password is to be changed
   * @return an {@link AuthResponse} object containing the logout information and a message
   *     indicating successful password change
   * @throws AuthorizationException if the provided current password does not match the stored
   *     password
   * @throws InvalidRequestException if:
   *     <ul>
   *       <li>The new password matches the current password.
   *       <li>The new password does not match the password confirmation.
   *     </ul>
   */
  AuthResponse changePassword(ChangePasswordRequest request, String userEmail);

  /**
   * Retrieves a user by their email address and maps the entity to a {@link AuthUserDto}.
   *
   * <p>This method fetches the user from the database using their email address and converts the
   * {@link User} entity to a {@code AuthUserDto}.
   *
   * @param email the email address of the user to retrieve.
   * @return an {@code AuthUserDto} containing the user's data.
   * @throws EntityNotFoundException if no user is found with the specified email.
   */
  AuthUserDto getUser(String email);

  /**
   * Retrieves all users from the database and maps them to a list of {@link AuthUserDto}.
   *
   * <p>This method fetches all user entities from the database and converts each {@link User}
   * entity to a {@code AuthUserDto} using the {@code userMapper}.
   *
   * @return a list of {@code AuthUserDto} objects, each representing a user.
   */
  List<AuthUserDto> getAllUsers();

  /**
   * Retrieves the authenticated user's details.
   *
   * <p>This method obtains the currently authenticated user's principal from the security context
   * and retrieves the corresponding {@link AuthUserDto}. If the user is not authenticated, an
   * {@link AuthorizationException} is thrown.
   *
   * @return the authenticated user's details as an AuthUserDto
   * @throws AuthorizationException if the user is not authenticated
   */
  AuthUserDto getUserByAuthentication();
}
