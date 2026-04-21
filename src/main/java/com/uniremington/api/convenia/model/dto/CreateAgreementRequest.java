package com.uniremington.api.convenia.model.dto;

import com.uniremington.api.convenia.model.entity.ContractType;
import com.uniremington.api.convenia.model.entity.PracticeComponent;
import com.uniremington.api.convenia.model.entity.PracticeModality;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload to create a new professional practice agreement in DRAFT status.
 *
 * @param studentId         Unique identifier of the student.
 * @param companyId         Unique identifier of the host company.
 * @param academicAdvisorId Optional — user ID of the academic advisor (required before submission).
 * @param companyRepId      Optional — user ID of the company representative (required before submission).
 * @param practiceModality  Practice modality (PROFESSIONAL, SOCIAL, RESEARCH, INTERNATIONAL).
 * @param practiceComponent Practice component (ACADEMIC, SOCIAL, MANAGEMENT).
 * @param contractType      Contract type (EMPLOYMENT, APPRENTICESHIP, INTERNSHIP_AGREEMENT, FRAMEWORK_AGREEMENT).
 * @param startDate         Start date of the practice period (ISO-8601).
 * @param endDate           End date — min 4 months from start, max 12; fixed 6 for APPRENTICESHIP.
 * @param weeklyHours       Weekly dedication hours; must be between 20 and 48 per regulation.
 * @param monthlyStipend    Monthly economic compensation; may be zero for unpaid internships.
 */
public record CreateAgreementRequest(

        @NotNull(message = "Student ID is required")
        Long studentId,

        @NotNull(message = "Company ID is required")
        Long companyId,

        Long academicAdvisorId,

        Long companyRepId,

        @NotNull(message = "Practice modality is required")
        PracticeModality practiceModality,

        @NotNull(message = "Practice component is required")
        PracticeComponent practiceComponent,

        @NotNull(message = "Contract type is required")
        ContractType contractType,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @NotNull(message = "Weekly hours is required")
        @Min(value = 20, message = "Weekly hours must be at least 20")
        @Max(value = 48, message = "Weekly hours must not exceed 48")
        Integer weeklyHours,

        @NotNull(message = "Monthly stipend is required")
        @DecimalMin(value = "0.00", message = "Monthly stipend cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Monthly stipend must have at most 10 integer digits and 2 decimal places")
        BigDecimal monthlyStipend

) {}
