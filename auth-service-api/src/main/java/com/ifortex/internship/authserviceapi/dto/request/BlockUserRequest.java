package com.ifortex.internship.authserviceapi.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BlockUserRequest {

  @NotNull(message = "User ID is required")
  @NotEmpty(message = "User ID can't be empty")
  private String userId;

  @JsonFormat(
      shape = JsonFormat.Shape.STRING,
      pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
      timezone = "UTC")
  @NotNull(message = "expiresAt is required")
  @Future(message = "expiresAt must be a future date")
  private LocalDateTime expiresAt;
}
