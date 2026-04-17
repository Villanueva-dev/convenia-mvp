package com.uniremington.api.convenia.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Input DTO for the login endpoint.
 *
 * <p>
 * Declared as a {@code record} because DTOs are immutable data containers —
 * they carry data in, never change, and carry data out.
 * </p>
 *
 * <p>
 * {@code @NotBlank} ensures the field is not null, empty, or whitespace-only.
 * {@code @Email} validates the format using Jakarta Bean Validation.
 * Both are enforced by the {@code @Valid} annotation in the controller.
 * </p>
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}
