package com.ifortex.internship.authserviceapi.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetRequest {
  @Email(message = "Invalid email format")
  @NotBlank(message = "Email is required")
  private String email;
}
