package com.uniremington.api.convenia.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GradeRequest(

        @NotNull(message = "Grade is required")
        @DecimalMin(value = "0.0", message = "Grade must be at least 0.0")
        @DecimalMax(value = "5.0", message = "Grade must be at most 5.0")
        BigDecimal grade

) {}
