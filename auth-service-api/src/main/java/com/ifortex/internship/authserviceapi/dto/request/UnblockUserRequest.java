package com.ifortex.internship.authserviceapi.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UnblockUserRequest {
    @NotNull(message = "Account ID is required")
    private UUID userId;
}
