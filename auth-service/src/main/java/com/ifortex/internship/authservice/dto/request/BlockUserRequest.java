package com.ifortex.internship.authservice.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class BlockUserRequest {

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
        timezone = "UTC")
    @NotNull(message = "expiresAt is required")
    @Future(message = "expiresAt must be a future date")
    private Instant expiresAt;
}
