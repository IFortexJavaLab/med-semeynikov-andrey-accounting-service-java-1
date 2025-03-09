package com.ifortex.internship.authservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class CreateParamedicRequest {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email;

    @NotNull(message = "Bonus policy ID is required")
    UUID bonusPolicyId;
}
