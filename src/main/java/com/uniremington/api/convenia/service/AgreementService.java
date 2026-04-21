package com.uniremington.api.convenia.service;

import com.uniremington.api.convenia.model.dto.*;
import com.uniremington.api.convenia.model.vo.JwtUser;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Business logic interface for the professional practice agreement lifecycle.
 *
 * <p>All methods enforce multi-tenancy: users can only access agreements within
 * their own university. ADMIN users (universityId == null) can access all universities.</p>
 */
public interface AgreementService {

    /**
     * Creates a new practice agreement in DRAFT status.
     *
     * <p>STUDENT users can only create agreements for their own student profile.
     * COORDINATOR and ADMIN users can create on behalf of any student in their university.</p>
     *
     * @param request     Validated request with agreement details.
     * @param currentUser The authenticated user performing the action.
     * @return The created agreement as a full response DTO.
     */
    AgreementResponse createAgreement(CreateAgreementRequest request, JwtUser currentUser);

    /**
     * Retrieves a single agreement by ID, enforcing tenant and ownership access control.
     *
     * @param id          The agreement unique identifier.
     * @param currentUser The authenticated user performing the action.
     * @return The agreement as a full response DTO.
     * @throws com.uniremington.api.convenia.shared.exception.ResourceNotFoundException if not found.
     */
    AgreementResponse getAgreement(Long id, JwtUser currentUser);

    /**
     * Lists agreements visible to the current user.
     *
     * <p>STUDENT sees only their own agreements. COORDINATOR/ADMIN see all
     * agreements within their university. ADMIN sees all universities.</p>
     *
     * @param currentUser The authenticated user performing the action.
     * @return List of agreement summaries ordered by creation date descending.
     */
    List<AgreementSummaryResponse> listAgreements(JwtUser currentUser);

    /**
     * Partially updates an agreement still in DRAFT status.
     *
     * <p>Only non-null fields in the request are applied. Once the agreement
     * leaves DRAFT, updates are rejected with a 409 Conflict.</p>
     *
     * @param id          The agreement unique identifier.
     * @param request     Fields to update (all optional).
     * @param currentUser The authenticated user performing the action.
     * @return The updated agreement as a full response DTO.
     * @throws IllegalStateException if the agreement is not in DRAFT status.
     */
    AgreementResponse updateAgreement(Long id, UpdateAgreementRequest request, JwtUser currentUser);

    /**
     * Deletes a DRAFT agreement permanently.
     *
     * <p>Only DRAFT agreements can be deleted. STUDENT users can only delete
     * their own agreements; ADMIN can delete any.</p>
     *
     * @param id          The agreement unique identifier.
     * @param currentUser The authenticated user performing the action.
     * @throws IllegalStateException if the agreement is not in DRAFT status.
     */
    void deleteAgreement(Long id, JwtUser currentUser);

    // ── Workflow transitions ───────────────────────────────────────────────────

    /**
     * Transitions a DRAFT agreement to ADMIN_REVIEW, initiating the approval workflow.
     *
     * <p>Validates that both academicAdvisor and companyRep are assigned before submission.
     * Only the owning student (or ADMIN) may submit.</p>
     *
     * @param id          The agreement unique identifier.
     * @param currentUser The authenticated user performing the action.
     * @return The updated agreement in ADMIN_REVIEW status.
     * @throws IllegalStateException    if the agreement is not in DRAFT status.
     * @throws IllegalArgumentException if required parties are missing.
     */
    AgreementResponse submitForReview(Long id, JwtUser currentUser);

    /**
     * Transitions an ADMIN_REVIEW agreement to COORDINATION_REVIEW.
     *
     * <p>Performed by ADMIN or COORDINATOR after verifying company documents
     * (RUT, NIT, Cámara de Comercio).</p>
     *
     * @param id          The agreement unique identifier.
     * @param currentUser The authenticated user performing the action.
     * @return The updated agreement in COORDINATION_REVIEW status.
     * @throws IllegalStateException if the agreement is not in ADMIN_REVIEW status.
     */
    AgreementResponse approveAdminReview(Long id, JwtUser currentUser);

    /**
     * Rejects an agreement at any review stage (ADMIN_REVIEW or COORDINATION_REVIEW).
     *
     * <p>The rejection reason is stored and visible to the student.</p>
     *
     * @param id          The agreement unique identifier.
     * @param reason      Human-readable explanation of the rejection.
     * @param currentUser The authenticated user performing the action.
     * @return The updated agreement in REJECTED status.
     * @throws IllegalStateException if the agreement is not in a reviewable status.
     */
    AgreementResponse rejectAgreement(Long id, String reason, JwtUser currentUser);

    /**
     * Transitions a COORDINATION_REVIEW agreement to PENDING_SIGNATURE.
     *
     * <p>Triggers PDF generation and uploads the document to Documenso for digital signing.
     * Signing invitations are sent to the student, company representative, and coordinator.</p>
     *
     * @param id          The agreement unique identifier.
     * @param currentUser The authenticated user performing the action (must be COORDINATOR or ADMIN).
     * @return The updated agreement in PENDING_SIGNATURE status with documensoDocumentId populated.
     * @throws IllegalStateException if the agreement is not in COORDINATION_REVIEW status.
     * @throws RuntimeException      if PDF generation or Documenso upload fails.
     */
    AgreementResponse endorseAgreement(Long id, JwtUser currentUser);

    /**
     * Transitions a PENDING_SIGNATURE agreement to ACTIVE after all parties have signed.
     *
     * <p>Called internally by {@code SignatureWebhookController} when Documenso
     * sends a {@code DOCUMENT_COMPLETED} event. The signed PDF URL is NOT included
     * in the Documenso v2 webhook — it must be retrieved separately via
     * {@code GET /envelope/download-item} using the stored {@code documensoDocumentId}.</p>
     *
     * @param documensoDocumentId The Documenso envelope ID from the webhook payload.
     * @throws com.uniremington.api.convenia.shared.exception.ResourceNotFoundException if no agreement matches the document ID.
     */
    void activateAgreement(String documensoDocumentId);

    /**
     * Formally closes an ACTIVE agreement (ACTIVE → COMPLETED).
     *
     * @param id          The agreement unique identifier.
     * @param currentUser The authenticated user (must be COORDINATOR or ADMIN).
     * @return The updated agreement in COMPLETED status.
     * @throws IllegalStateException if the agreement is not in ACTIVE status.
     */
    AgreementResponse completeAgreement(Long id, JwtUser currentUser);

    /**
     * Submits a grade (0.0–5.0) for an ACTIVE agreement.
     *
     * <p>ACADEMIC_ADVISOR sets {@code advisorGrade}; COMPANY_TUTOR sets {@code companyGrade}.
     * When both grades are present the service computes {@code finalGrade = (a + c) / 2}.</p>
     *
     * @param id          Agreement unique identifier.
     * @param request     Grade value.
     * @param currentUser Must be the assigned advisor or company tutor.
     * @return Updated agreement.
     */
    AgreementResponse gradeAgreement(Long id, GradeRequest request, JwtUser currentUser);

    /**
     * Uploads a student document to Cloudflare R2 and stores the file key on the agreement.
     *
     * @param id          Agreement unique identifier.
     * @param type        Document type determining which field is updated.
     * @param file        Uploaded file.
     * @param currentUser Must be the student who owns the agreement.
     * @return Updated agreement.
     * @throws IOException if reading the file bytes fails.
     */
    AgreementResponse uploadDocument(Long id, DocumentType type, MultipartFile file, JwtUser currentUser)
            throws IOException;
}
