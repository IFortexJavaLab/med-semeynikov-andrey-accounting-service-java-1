package com.ifortex.internship.authservice.dto.request;

import com.ifortex.internship.authservice.dto.validation.PasswordMatch;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@PasswordMatch
public record RegistrationRequest(

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    String lastName,

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^\\+?\\d{10,15}$",
        message = "Phone number must be valid and contain 10 to 15 digits"
    )
    String phoneNumber,

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long.")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$",
        message = "Password must contain at least 1 uppercase letter, 1 number, and 1 special character."
    )
    String password,

    @NotBlank(message = "Password confirmation is required")
    String passwordConfirmation
) {
}
