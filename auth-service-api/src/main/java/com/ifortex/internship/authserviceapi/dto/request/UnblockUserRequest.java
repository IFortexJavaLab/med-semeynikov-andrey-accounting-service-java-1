package com.ifortex.internship.authserviceapi.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnblockUserRequest {
  @NotNull(message = "User ID is required")
  @NotEmpty(message = "User ID can't be empty")
  private String userId;
}
