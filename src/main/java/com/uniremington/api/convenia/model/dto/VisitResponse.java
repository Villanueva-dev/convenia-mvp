package com.uniremington.api.convenia.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VisitResponse(
        Long id,
        Long agreementId,
        String advisorEmail,
        LocalDate visitDate,
        String visitType,
        String observations,
        LocalDateTime createdAt
) {}
