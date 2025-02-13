package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.exception.custom.*;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import com.ifortex.internship.authserviceapi.dto.request.CreateUserRequest;
import com.ifortex.internship.authserviceapi.dto.request.LoginRequest;
import com.ifortex.internship.authserviceapi.dto.request.PasswordResetWithOtpDto;
import com.ifortex.internship.authserviceapi.dto.request.RegistrationRequest;
import com.ifortex.internship.authserviceapi.dto.request.VerifyLoginOtpRequest;
import com.ifortex.internship.authserviceapi.dto.response.AuthResponse;
import com.ifortex.internship.authserviceapi.dto.response.CreateUserResponse;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import com.ifortex.internship.authserviceapi.dto.response.TemporaryPasswordResponse;
import java.util.List;

/**
 * Service interface for handling user login and authentication.
 *
 * <p>Provides methods to authenticate users, generate authentication tokens.
 */
public interface AuthService {

  /**
   * Registers a new user in the system.
   *
   * <p>This method validates the registration request, including email uniqueness and password
   * confirmation. If valid, it hashes the user's password, assigns the default "non-subscribed
   * user" role, and saves the user in the database.
   *
   * @param request the registration request containing user details like email, password, and
   *     password confirmation
   * @throws EmailAlreadyRegistered if the email is already registered in the system
   * @throws InvalidRequestException if the provided password and its confirmation do not match
   * @throws EntityNotFoundException if the default "non-subscribed user" role is not found in the
   *     database
   */
  void registerUser(RegistrationRequest request);

  /**
   * Creates a new user with the provided roles and email.
   *
   * @param request the {@link CreateUserRequest} containing the email and list of roles to assign
   *     to the user.
   * @return CreateUserResponse containing a success message and the generated temporary password.
   * @throws EmailAlreadyRegistered if the provided email is already registered in the system.
   * @throws RegistrationFailedException if the user creation fails during the interaction with the
   *     User Management Service.
   */
  CreateUserResponse createUser(CreateUserRequest request);

  /**
   * Authenticates a user based on the provided login credentials.
   *
   * <p>This method validates the user's email and password using Spring Security's authentication
   * manager. If the user has two-factor authentication (2FA) enabled, an OTP (one-time password) is
   * generated and sent to the user's email. The method then responds with a message instructing the
   * user to complete the 2FA verification.
   *
   * <p>If 2FA is not enabled, the method generates access and refresh tokens for the user and
   * returns them in the response.
   *
   * @param loginRequest the {@link LoginRequest} containing the user's email and password
   * @return an {@link AuthResponse} containing a message and either instructions for 2FA or
   *     authentication tokens
   * @throws EmailSendException if an error occurs while sending the OTP email for 2FA
   */
  AuthResponse authenticateUser(LoginRequest loginRequest);

  /**
   * Verifies the one-time password (OTP) for two-factor authentication during login.
   *
   * <p>This method checks the provided OTP against the one stored for the given user email. If the
   * OTP is valid, it generates authentication tokens (access and refresh) for the user and returns
   * them in an {@link AuthResponse}.
   *
   * @param verifyLoginOtpRequest the {@link VerifyLoginOtpRequest} containing the user's email and
   *     OTP.
   * @return an AuthResponse containing the authentication tokens and a success message.
   * @throws AuthorizationException if the OTP is expired or invalid.
   * @throws EntityNotFoundException if no user is found with the provided email address.
   */
  AuthResponse completeLoginWithOtp(VerifyLoginOtpRequest verifyLoginOtpRequest);

  /**
   * Logs out the currently authenticated user.
   *
   * @return an {@link AuthResponse} containing a success message
   * @throws AuthorizationException if the user is not authenticated
   */
  AuthResponse logoutUser();

  /**
   * Initiates the password reset process for a user.
   *
   * <p>This method verifies that the provided email address is registered in the system. If the
   * user is found, a one-time password (OTP) is generated and saved with a defined expiration time.
   * An email is sent to the user containing the OTP and a link to reset their password.
   *
   * @param email the password reset request containing the user's email address.
   * @return a {@link SuccessResponse} containing a message confirming the initiation of the
   *     password reset process and instructions to complete it.
   * @throws EntityNotFoundException if no user is found with the provided email address.
   * @throws EmailSendException if an error occurs while sending the email with the OTP.
   */
  SuccessResponse initiatePasswordReset(String email);

  /**
   * Resets the user's password using a one-time password (OTP) sent to their email.
   *
   * @param passwordResetWithOtpDto the {@link PasswordResetWithOtpDto} containing the user's email,
   *     OTP, new password, and password confirmation.
   * @return a {@link SuccessResponse} containing a message indicating that the password was
   *     successfully reset.
   * @throws AuthorizationException if the provided OTP does not match the stored OTP.
   * @throws InvalidRequestException if the new password and its confirmation do not match.
   */
  SuccessResponse resetPasswordWithOtp(PasswordResetWithOtpDto passwordResetWithOtpDto);

  /**
   * Generates a random 6-digit one-time password (OTP) for authentication purposes.
   *
   * @return a 6-digit OTP as a String
   */
  String generateOtp();

  /**
   * Retrieves the email of the currently authenticated user from the security context.
   *
   * @return the email of the authenticated user
   * @throws AuthorizationException if the user is not authenticated or is anonymous
   */
  String getUserEmailFromAuthentication();

  /**
   * Retrieves the unique user ID of the currently authenticated user from the security context.
   *
   * @return the user ID of the authenticated user
   * @throws AuthorizationException if the user is not authenticated or is anonymous
   */
  String getUserIdFromAuthentication();

  /**
   * Retrieves the roles of the currently authenticated user from the security context.
   *
   * @return the user ID of the authenticated user
   * @throws AuthorizationException if the user is not authenticated or is anonymous
   */
  List<String> getUserRolesFromAuthentication();

  /**
   * Retrieves a list of {@link AuthUserDto} based on provided filters.
   *
   * <p>This method fetches users by their IDs and applies optional filters for roles, status, and
   * email. It then maps the filtered users to their corresponding {@link AuthUserDto}
   * representation.
   *
   * @param userIds List of user IDs to search for. Cannot be null.
   * @param roles Optional list of roles to filter users by (e.g., "ADMIN", "USER").
   * @param status Optional user status to filter by (e.g., "ACTIVE", "BLOCKED").
   * @param email Optional email to filter users (supports partial matching, case-insensitive).
   * @return List of {@link AuthUserDto} representing the filtered users.
   */
  List<AuthUserDto> searchUsers(
      List<String> userIds, List<String> roles, String status, String email);

  /**
   * Resets the password for the user with the specified userId by generating a temporary password.
   * The method performs validation to ensure that only super admins can reset passwords for super
   * admins. Deletes any existing temporary passwords and deletes refresh tokens for the user.
   *
   * @param userId the ID of the user whose password is to be reset
   * @return a response containing the newly generated temporary password
   * @throws EntityNotFoundException if the user with the given ID is not found
   * @throws SuperAdminModificationException if a non-super admin attempts to modify a super admin's
   *     password
   */
  TemporaryPasswordResponse resetPasswordWithTemp(String userId);

  /**
   * Initiates a password reset request for a user by setting their current password to null and
   * sending a password reset request email to the user's email address.
   *
   * <p>The user will receive an email with a link to reset their password. The email is generated
   * using the provided template and sent through the email service. The user's password is set to
   * null, and the associated refresh token is deleted.
   *
   * @param userId the ID of the user whose password is to be reset
   * @return a {@link SuccessResponse} containing a message confirming that the password reset email
   *     was sent
   * @throws EntityNotFoundException if the user with the specified ID does not exist
   * @throws EmailSendException if an error occurs while sending the password reset email
   */
  SuccessResponse resetPasswordWithEmail(String userId);
}
