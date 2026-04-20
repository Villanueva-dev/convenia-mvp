package com.uniremington.api.convenia.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lightweight response payload used in agreement list endpoints.
 *
 * <p>Contains only the fields necessary for rendering a list row,
 * avoiding the overhead of full entity hydration.</p>
 *
 * @param id          Agreement unique identifier.
 * @param studentName Student full legal name.
 * @param companyName Host company legal name.
 * @param status      Current workflow status name (e.g., "DRAFT", "ACTIVE").
 * @param startDate   Practice start date.
 * @param endDate     Practice end date.
 * @param createdAt   Timestamp when the agreement was created.
 */
public record AgreementSummaryResponse(
        Long id,
        String studentName,
        String companyName,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt
) {}
