package com.ifortex.internship.authserviceapi.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetWithOtpDto {
  @Email(message = "Invalid email format")
  @NotBlank(message = "Email is required")
  private String email;

  @NotBlank(message = "One time password is required")
  @Pattern(regexp = "\\d{6}", message = "One time password must consist of exactly 6 digits")
  private String otp;

  @NotBlank
  @Size(min = 8, message = "Password must be at least 8 characters long.")
  @Pattern(
      regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$",
      message =
          "Password must contain at least 1 uppercase letter, 1 number, and 1 special character.")
  private String newPassword;

  @NotBlank private String passwordConfirmation;
}
