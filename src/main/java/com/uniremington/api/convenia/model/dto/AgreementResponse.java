package com.uniremington.api.convenia.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Full response payload for a professional practice agreement resource.
 *
 * @param id                    Agreement unique identifier.
 * @param universityId          Tenant university identifier.
 * @param studentId             Student identifier.
 * @param studentName           Student full legal name.
 * @param companyId             Host company identifier.
 * @param companyName           Host company legal name.
 * @param academicAdvisorId     Academic advisor user ID (nullable until assigned).
 * @param academicAdvisorEmail  Academic advisor email (nullable until assigned).
 * @param companyRepId          Company representative user ID (nullable until assigned).
 * @param companyRepEmail       Company representative email (nullable until assigned).
 * @param practiceModality      Practice modality enum name.
 * @param practiceComponent     Practice component enum name.
 * @param contractType          Contract type enum name.
 * @param startDate             Practice start date.
 * @param endDate               Practice end date.
 * @param weeklyHours           Weekly dedication hours.
 * @param monthlyStipend        Monthly economic compensation.
 * @param status                Current workflow status name.
 * @param rejectionReason       Reason provided when status is REJECTED or returned to DRAFT; null otherwise.
 * @param documensoDocumentId   Documenso document ID set when agreement reaches PENDING_SIGNATURE.
 * @param pdfCloudUrl           URL to the generated agreement PDF.
 * @param advisorGrade          Academic advisor's grade (0.0–5.0); null until EVALUATION phase.
 * @param companyGrade          Company representative's grade (0.0–5.0); null until EVALUATION phase.
 * @param finalGrade            Calculated final grade (average of advisor and company grades).
 * @param cvFileKey             R2 key for the student CV; null if not yet uploaded.
 * @param contractFileKey       R2 key for the scanned labor contract; null if not yet uploaded.
 * @param nationalIdFileKey     R2 key for the student national ID copy; null if not yet uploaded.
 * @param epsFileKey            R2 key for the EPS health certificate; null if not yet uploaded.
 * @param arlFileKey            R2 key for the ARL occupational risk certificate; null if not yet uploaded.
 * @param workPlanFileKey       R2 key for the agreed work plan; null if not yet uploaded.
 * @param nitFileKey            R2 key for the company NIT certificate; null if not yet uploaded.
 * @param rutFileKey            R2 key for the company RUT document; null if not yet uploaded.
 * @param camaraComercioFileKey R2 key for the Cámara de Comercio certificate; null if not yet uploaded.
 * @param createdAt             Timestamp when the agreement was created.
 * @param updatedAt             Timestamp of the last update.
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
        String practiceComponent,
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
        String cvFileKey,
        String contractFileKey,
        String nationalIdFileKey,
        String epsFileKey,
        String arlFileKey,
        String workPlanFileKey,
        String nitFileKey,
        String rutFileKey,
        String camaraComercioFileKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
