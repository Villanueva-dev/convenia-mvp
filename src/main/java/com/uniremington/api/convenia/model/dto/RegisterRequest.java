package com.uniremington.api.convenia.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email   String email,
        @NotBlank @Size(min = 8) String password,
        @NotNull           Long   universityId,

        // Required only for STUDENT role
        String  fullName,
        String  documentNumber,
        String  phoneNumber,
        Long    academicProgramId,
        Integer currentSemester
) {}
