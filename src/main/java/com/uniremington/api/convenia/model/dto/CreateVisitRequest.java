package com.uniremington.api.convenia.model.dto;

import com.uniremington.api.convenia.model.entity.VisitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateVisitRequest(

        @NotNull(message = "Visit date is required")
        LocalDate visitDate,

        @NotNull(message = "Visit type is required")
        VisitType visitType,

        @NotBlank(message = "Observations are required")
        @Size(min = 10, max = 2000, message = "Observations must be between 10 and 2000 characters")
        String observations

) {}
