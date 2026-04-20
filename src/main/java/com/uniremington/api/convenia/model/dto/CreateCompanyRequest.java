package com.uniremington.api.convenia.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(
        @NotBlank String legalName,
        @NotBlank String nit,
        @NotBlank String representativeName,
        @NotBlank @Email String representativeEmail
) {}
