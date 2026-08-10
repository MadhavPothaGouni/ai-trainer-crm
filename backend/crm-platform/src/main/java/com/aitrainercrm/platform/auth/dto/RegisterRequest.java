package com.aitrainercrm.platform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,

        // At least one lowercase, one uppercase, one digit, one special char - checked here
        // (fast, clear field-level error) rather than only in the service layer.
        @NotBlank
                @Size(min = 8, max = 100)
                @Pattern(
                        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).+$",
                        message = "Password must contain an uppercase letter, a lowercase letter, a digit, and a special character")
                String password,

        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,

        /** Optional: joining an existing org via invite. Null means "create a new organization." */
        String organizationName) {
}
