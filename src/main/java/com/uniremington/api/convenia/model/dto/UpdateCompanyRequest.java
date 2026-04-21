package com.uniremington.api.convenia.model.dto;

import jakarta.validation.constraints.Email;

public record UpdateCompanyRequest(
        String legalName,
        String representativeName,
        @Email(message = "Representative email must be valid") String representativeEmail
) {}
