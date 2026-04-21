package com.uniremington.api.convenia.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank @Email(message = "Valid email is required")
        String email,

        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotNull(message = "University ID is required")
        Long universityId,

        @NotNull(message = "Role is required")
        AllowedRole role

) {}
