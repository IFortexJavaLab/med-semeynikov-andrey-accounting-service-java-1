package com.ifortex.internship.authservice.dto.request;

import com.ifortex.internship.authservice.dto.validation.PasswordMatch;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@PasswordMatch
public class RegistrationRequest {
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters long.")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$",
        message =
            "Password must contain at least 1 uppercase letter, 1 number, and 1 special character.")
    private String password;

    @NotBlank private String passwordConfirmation;
}
