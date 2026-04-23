package com.uniremington.api.convenia.service;

import com.uniremington.api.convenia.model.dto.*;
import com.uniremington.api.convenia.model.entity.DocumentType;
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
     * Transitions an ACTIVE agreement to EVALUATION (ACTIVE → EVALUATION).
     *
     * <p>Triggered by the coordinator when the internship period has ended.
     * Once in EVALUATION, both the academic advisor and company tutor must
     * submit their grades. The agreement transitions automatically to
     * {@link com.uniremington.api.convenia.model.entity.AgreementStatus#FINISHED}
     * when both grades are recorded.</p>
     *
     * @param id          The agreement unique identifier.
     * @param currentUser The authenticated user (must be COORDINATOR or ADMIN).
     * @return The updated agreement in EVALUATION status.
     * @throws IllegalStateException if the agreement is not in ACTIVE status.
     */
    AgreementResponse startEvaluation(Long id, JwtUser currentUser);

    /**
     * Submits a grade (0.0–5.0) for an agreement in EVALUATION status.
     *
     * <p>ACADEMIC_ADVISOR sets {@code advisorGrade}; COMPANY_TUTOR sets {@code companyGrade}.
     * When both grades are present the service computes {@code finalGrade = (a + c) / 2}
     * and automatically transitions the agreement to FINISHED.</p>
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

    /**
     * Lists the metadata of all documents uploaded for a given agreement.
     *
     * <p>Tenant access is enforced; STUDENT users only see their own agreements.</p>
     *
     * @param id          Agreement unique identifier.
     * @param currentUser Authenticated user.
     * @return List of document metadata; empty if nothing has been uploaded yet.
     */
    List<AgreementDocumentResponse> listDocuments(Long id, JwtUser currentUser);

    /**
     * Downloads a previously uploaded document from Cloudflare R2.
     *
     * <p>Any authenticated user with tenant access can download documents —
     * secretaries and coordinators need them to validate submissions.</p>
     *
     * @param id          Agreement unique identifier.
     * @param type        Document type to download.
     * @param currentUser Authenticated user (tenant access enforced).
     * @return Raw file bytes.
     * @throws com.uniremington.api.convenia.shared.exception.ResourceNotFoundException if the document has not been uploaded yet.
     */
    byte[] downloadDocument(Long id, DocumentType type, JwtUser currentUser);

    /**
     * Downloads the "Constancia de Culminación" PDF for a FINISHED agreement.
     *
     * <p>Generates the PDF on the first request and caches it in Cloudflare R2
     * keyed as {@code certificates/{universityId}/{agreementId}.pdf}.
     * Subsequent requests serve the cached object directly.</p>
     *
     * <p>Access is restricted to the agreement's student (owner), its assigned
     * academic advisor, its assigned company tutor, any coordinator within the
     * same university, and any ADMIN.</p>
     *
     * @param id          Agreement unique identifier.
     * @param currentUser Authenticated user (access enforced per role).
     * @return Raw PDF bytes.
     * @throws IllegalStateException  if the agreement is not in FINISHED status.
     * @throws org.springframework.security.access.AccessDeniedException if the user is not authorized for this agreement.
     */
    byte[] downloadCertificate(Long id, JwtUser currentUser);
}
