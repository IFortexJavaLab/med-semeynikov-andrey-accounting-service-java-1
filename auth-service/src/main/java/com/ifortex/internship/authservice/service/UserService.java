package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.InvalidRequestException;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;

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
}
