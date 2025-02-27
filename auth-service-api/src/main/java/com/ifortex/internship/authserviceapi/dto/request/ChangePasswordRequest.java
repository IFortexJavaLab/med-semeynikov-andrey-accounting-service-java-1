package com.ifortex.internship.authserviceapi.dto.request;

import com.ifortex.internship.authserviceapi.validation.PasswordMatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@PasswordMatch(passwordField = "newPassword")
public class ChangePasswordRequest {

  @NotBlank private String currentPassword;

  @NotBlank
  @Size(min = 8, message = "Password must be at least 8 characters long.")
  @Pattern(
      regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$",
      message =
          "Password must contain at least 1 uppercase letter, 1 number, and 1 special character.")
  private String newPassword;

  @NotBlank private String passwordConfirmation;
}
