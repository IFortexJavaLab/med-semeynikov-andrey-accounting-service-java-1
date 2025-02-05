package com.ifortex.internship.authserviceapi.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateUserRequest {

  @Email(message = "Invalid email format")
  @NotBlank(message = "Email is required")
  private String email;

  @NotEmpty(message = "Roles can't be empty")
  private List<String> roles;

  private String bonusPolicyId;
}
