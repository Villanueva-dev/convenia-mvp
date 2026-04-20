package com.uniremington.api.convenia.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Full response payload for a professional practice agreement resource.
 *
 * @param id                   Agreement unique identifier.
 * @param universityId         Tenant university identifier.
 * @param studentId            Student identifier.
 * @param studentName          Student full legal name.
 * @param companyId            Host company identifier.
 * @param companyName          Host company legal name.
 * @param academicAdvisorId    Academic advisor user ID (nullable until assigned).
 * @param academicAdvisorEmail Academic advisor email (nullable until assigned).
 * @param companyRepId         Company representative user ID (nullable until assigned).
 * @param companyRepEmail      Company representative email (nullable until assigned).
 * @param practiceModality     Practice modality name (e.g., "PROFESSIONAL").
 * @param contractType         Contract type name (e.g., "APPRENTICESHIP").
 * @param startDate            Practice start date.
 * @param endDate              Practice end date.
 * @param weeklyHours          Weekly dedication hours.
 * @param monthlyStipend       Monthly economic compensation.
 * @param status               Current workflow status name (e.g., "DRAFT", "ACTIVE").
 * @param rejectionReason      Reason provided when status is REJECTED; null otherwise.
 * @param documensoDocumentId  Documenso document ID set when agreement reaches PENDING_SIGNATURE.
 * @param pdfCloudUrl          URL to the generated agreement PDF; available after PENDING_SIGNATURE.
 * @param advisorGrade         Academic advisor's grade (0.0–5.0); null until ACTIVE phase.
 * @param companyGrade         Company representative's grade (0.0–5.0); null until ACTIVE phase.
 * @param finalGrade           Calculated final grade (average of advisor and company grades).
 * @param createdAt            Timestamp when the agreement was created.
 * @param updatedAt            Timestamp of the last update.
 */
public record AgreementResponse(
        Long id,
        Long universityId,
        Long studentId,
        String studentName,
        Long companyId,
        String companyName,
        Long academicAdvisorId,
        String academicAdvisorEmail,
        Long companyRepId,
        String companyRepEmail,
        String practiceModality,
        String contractType,
        LocalDate startDate,
        LocalDate endDate,
        Integer weeklyHours,
        BigDecimal monthlyStipend,
        String status,
        String rejectionReason,
        String documensoDocumentId,
        String pdfCloudUrl,
        BigDecimal advisorGrade,
        BigDecimal companyGrade,
        BigDecimal finalGrade,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
