package com.uniremington.api.convenia.model.dto;

import com.uniremington.api.convenia.model.entity.ContractType;
import com.uniremington.api.convenia.model.entity.PracticeComponent;
import com.uniremington.api.convenia.model.entity.PracticeModality;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload to partially update a practice agreement still in DRAFT status.
 *
 * <p>All fields are optional. Only non-null values are applied to the existing agreement.
 * Once the agreement leaves DRAFT status, updates are no longer permitted.</p>
 *
 * @param companyId         New host company identifier.
 * @param academicAdvisorId New academic advisor user ID.
 * @param companyRepId      New company representative user ID.
 * @param practiceModality  New practice modality (PROFESSIONAL, SOCIAL, RESEARCH, INTERNATIONAL).
 * @param practiceComponent New practice component (ACADEMIC, SOCIAL, MANAGEMENT).
 * @param contractType      New contract type.
 * @param startDate         New start date.
 * @param endDate           New end date.
 * @param weeklyHours       New weekly hours (20–48).
 * @param monthlyStipend    New monthly stipend (≥ 0).
 */
public record UpdateAgreementRequest(

        Long companyId,

        Long academicAdvisorId,

        Long companyRepId,

        PracticeModality practiceModality,

        PracticeComponent practiceComponent,

        ContractType contractType,

        LocalDate startDate,

        LocalDate endDate,

        @Min(value = 20, message = "Weekly hours must be at least 20")
        @Max(value = 48, message = "Weekly hours must not exceed 48")
        Integer weeklyHours,

        @DecimalMin(value = "0.00", message = "Monthly stipend cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Monthly stipend must have at most 10 integer digits and 2 decimal places")
        BigDecimal monthlyStipend

) {}
