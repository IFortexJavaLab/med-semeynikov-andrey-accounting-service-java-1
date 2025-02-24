package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EmailSendException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.ForbiddenActionException;
import com.ifortex.internship.authservice.exception.custom.InternalAuthServiceException;
import com.ifortex.internship.authservice.exception.custom.InvalidRequestException;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.BlockUserRequest;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.request.TwoFactorAuthRequest;
import com.ifortex.internship.authserviceapi.dto.request.UnblockUserRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.ChangeEmailResponse;
import java.util.List;

/**
 * Service interface for managing user-related operations.
 *
 * <p>Provides methods for finding users by their unique identifiers or email addresses, as well as
 * updating sensitive information such as passwords.
 */
public interface UserService {

  /**
   * Changes the password of a user.
   *
   * <p>This method authenticates the user's current password, checks that the new password is
   * different from the current one, and ensures the new password matches the confirmation. If all
   * checks pass, it updates the user's password and clears any temporary password. It also logs the
   * user out and provides a link to the login page with a success message.
   *
   * @param request the request containing the current password, new password, and password
   *     confirmation
   * @param userEmail the email of the user whose password is to be changed
   * @return an {@link AuthResponse} object containing a success message, logout information, and a
   *     login link
   * @throws AuthorizationException if the provided current password does not match the stored
   *     password
   * @throws InvalidRequestException if:
   *     <ul>
   *       <li>The new password matches the current password
   *       <li>The new password does not match the password confirmation
   *     </ul>
   */
  AuthResponse changePassword(ChangePasswordRequest request, String userEmail);

  /**
   * Changes the email address of the currently authenticated user. If the verification code (OTP)
   * is not provided, the method generates and sends a new OTP to the user's current email. If the
   * OTP is provided, the method verifies it and updates the user's email address accordingly.
   *
   * @param newEmail The new email address to update.
   * @param code The one-time password (OTP) required for email verification (optional).
   * @return A {@link ChangeEmailResponse} containing either a success message or the updated user
   *     information.
   * @throws InvalidRequestException if the new email is the same as the current one.
   * @throws EmailSendException if an error occurs while sending the verification email.
   * @throws AuthorizationException if the OTP is expired, invalid, or the user cannot be verified.
   */
  ChangeEmailResponse changeEmail(String newEmail, String code);

  /**
   * Retrieves a user by their userId address and maps the entity to a {@link AuthUserDto}.
   *
   * <p>This method fetches the user from the database using their userId address and converts the
   * {@link User} entity to a {@code AuthUserDto}.
   *
   * @param userId the userId address of the user to retrieve.
   * @return an {@code AuthUserDto} containing the user's data.
   * @throws EntityNotFoundException if no user is found with the specified userId.
   */
  AuthUserDto getUserByUserId(String userId);

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
  AuthUserDto getUser();

  /**
   * Changes the two-factor authentication (2FA) status for the currently authenticated user. If no
   * new 2FA state is provided in the request, the method returns the user's current data. If the
   * 2FA state remains unchanged, the method also returns the user without making any updates.
   *
   * @param request The request object containing the new 2FA state (true/false).
   * @return {@link AuthUserDto} representing the user with the updated or unchanged 2FA state.
   */
  AuthUserDto changeTwoFactorAuth(TwoFactorAuthRequest request);

  /**
   * Changes the two-factor authentication (2FA) status for a specific user. This method is intended
   * for administrators to manage 2FA settings for other users. If no new 2FA state is provided in
   * the request, the method returns the user's current data. If the 2FA state remains unchanged,
   * the method also returns the user without making any updates.
   *
   * @param userId The ID of the user whose 2FA status is being changed.
   * @param request The request object containing the new 2FA state (true/false).
   * @return {@link AuthUserDto} representing the user with the updated or unchanged 2FA state.
   */
  AuthUserDto changeTwoFactorAuthByAdmin(String userId, TwoFactorAuthRequest request);

  /**
   * Blocks a user until the specified expiration date.
   *
   * @param request The block request containing user ID and expiration date.
   * @throws ForbiddenActionException if a user attempts to block themselves or if a non-super admin
   *     attempts to block a super admin.
   */
  void blockUser(BlockUserRequest request);

  /**
   * Unblocks a previously blocked user.
   *
   * @param request The unblock request containing the user ID.
   * @throws ForbiddenActionException if a user attempts to unblock themselves or if a non-super
   *     admin attempts to unblock a super admin.
   */
  void unblockUser(UnblockUserRequest request);

  /**
   * Performs a soft delete operation on a user.
   *
   * <p>This method marks the user as soft deleted by setting the {@code softDeleted} flag to {@code
   * true}
   *
   * @param userId The ID of the user to be soft deleted.
   * @throws ForbiddenActionException If the user attempts to delete themselves or if a non-super *
   *     admin attempts to delete a super admin.
   * @throws EntityNotFoundException If no user is found with the provided {@code userId}.
   */
  void softDeleteUser(String userId);

  /**
   * Performs a hard deletion of a user with the specified user ID. This method removes the user
   * from the database and also attempts to delete their associated records from external services
   * such as the User Management API and Stripe.
   *
   * <p>If an error occurs while calling external services, an {@link InternalAuthServiceException}
   * is thrown.
   *
   * @param userId the unique identifier of the user to be deleted.
   * @throws InternalAuthServiceException if an error occurs while deleting the user in external
   *     services (User Management or Stripe).
   */
  void hardDelete(String userId);
}
