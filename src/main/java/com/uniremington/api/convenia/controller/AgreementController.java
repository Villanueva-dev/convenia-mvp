package com.uniremington.api.convenia.controller;

import com.uniremington.api.convenia.model.dto.*;
import com.uniremington.api.convenia.model.entity.DocumentType;
import com.uniremington.api.convenia.model.vo.JwtUser;
import com.uniremington.api.convenia.service.AgreementService;
import com.uniremington.api.convenia.service.DocumensoService;
import com.uniremington.api.convenia.service.VisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Handles HTTP requests for the professional practice agreement lifecycle.
 *
 * <p>All endpoints require a valid JWT. Fine-grained authorization is enforced
 * both here (via {@code @PreAuthorize}) and in the service layer (tenant isolation,
 * student ownership checks).</p>
 */
@Tag(name = "Agreements", description = "Professional practice agreement management and workflow")
@RestController
@RequestMapping("/api/v1/agreements")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AgreementController {

    private final AgreementService agreementService;
    private final DocumensoService  documensoService;
    private final VisitService      visitService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * Creates a new practice agreement in DRAFT status.
     *
     * @param request     Validated agreement creation payload.
     * @param currentUser The authenticated user (injected from JWT).
     * @return 201 Created with the created agreement.
     */
    @Operation(summary = "Create agreement", description = "Creates a new agreement in DRAFT status")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Agreement created"),
            @ApiResponse(responseCode = "400", description = "Validation error or business rule violation"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT', 'COORDINATOR', 'ADMIN')")
    public ResponseEntity<AgreementResponse> createAgreement(
            @Valid @RequestBody CreateAgreementRequest request,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agreementService.createAgreement(request, currentUser));
    }

    /**
     * Retrieves a single agreement by ID.
     *
     * @param id          Agreement unique identifier.
     * @param currentUser The authenticated user.
     * @return 200 OK with the agreement details.
     */
    @Operation(summary = "Get agreement", description = "Retrieves a single agreement by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agreement found"),
            @ApiResponse(responseCode = "404", description = "Agreement not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AgreementResponse> getAgreement(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.ok(agreementService.getAgreement(id, currentUser));
    }

    /**
     * Lists agreements visible to the current user.
     *
     * <p>STUDENT sees their own; COORDINATOR/ADMIN see all in their university.</p>
     *
     * @param currentUser The authenticated user.
     * @return 200 OK with a list of agreement summaries.
     */
    @Operation(summary = "List agreements", description = "Lists agreements accessible to the current user")
    @ApiResponse(responseCode = "200", description = "Agreements listed")
    @GetMapping
    public ResponseEntity<List<AgreementSummaryResponse>> listAgreements(
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.ok(agreementService.listAgreements(currentUser));
    }

    /**
     * Partially updates a DRAFT agreement.
     *
     * @param id          Agreement unique identifier.
     * @param request     Fields to update (all optional).
     * @param currentUser The authenticated user.
     * @return 200 OK with the updated agreement.
     */
    @Operation(summary = "Update agreement", description = "Partially updates a DRAFT agreement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agreement updated"),
            @ApiResponse(responseCode = "409", description = "Agreement is not in DRAFT status")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'COORDINATOR', 'ADMIN')")
    public ResponseEntity<AgreementResponse> updateAgreement(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAgreementRequest request,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.ok(agreementService.updateAgreement(id, request, currentUser));
    }

    /**
     * Deletes a DRAFT agreement permanently.
     *
     * @param id          Agreement unique identifier.
     * @param currentUser The authenticated user.
     * @return 204 No Content.
     */
    @Operation(summary = "Delete agreement", description = "Deletes a DRAFT agreement permanently")
    @ApiResponse(responseCode = "204", description = "Agreement deleted")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'COORDINATOR', 'ADMIN')")
    public ResponseEntity<Void> deleteAgreement(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {

        agreementService.deleteAgreement(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    // ── Workflow transitions ───────────────────────────────────────────────────

    /**
     * Submits a DRAFT agreement for administrative review (DRAFT → ADMIN_REVIEW).
     *
     * @param id          Agreement unique identifier.
     * @param currentUser The authenticated user (must be the student owner or ADMIN).
     * @return 200 OK with the updated agreement in ADMIN_REVIEW status.
     */
    @Operation(summary = "Submit for review", description = "Transitions DRAFT → ADMIN_REVIEW")
    @ApiResponse(responseCode = "200", description = "Agreement submitted for review")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<AgreementResponse> submitForReview(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.ok(agreementService.submitForReview(id, currentUser));
    }

    /**
     * Approves the administrative review stage (ADMIN_REVIEW → COORDINATION_REVIEW).
     *
     * @param id          Agreement unique identifier.
     * @param currentUser The authenticated user (must be COORDINATOR or ADMIN).
     * @return 200 OK with the updated agreement in COORDINATION_REVIEW status.
     */
    @Operation(summary = "Approve admin review", description = "Transitions ADMIN_REVIEW → COORDINATION_REVIEW")
    @ApiResponse(responseCode = "200", description = "Agreement advanced to coordination review")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SECRETARY', 'COORDINATOR', 'ADMIN')")
    public ResponseEntity<AgreementResponse> approveAdminReview(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.ok(agreementService.approveAdminReview(id, currentUser));
    }

    /**
     * Rejects an agreement at any review stage, storing the rejection reason.
     *
     * @param id          Agreement unique identifier.
     * @param request     Rejection reason payload.
     * @param currentUser The authenticated user (must be COORDINATOR or ADMIN).
     * @return 200 OK with the updated agreement in REJECTED status.
     */
    @Operation(summary = "Reject agreement",
            description = "ADMIN_REVIEW → returns to DRAFT for corrections. COORDINATION_REVIEW → terminal REJECTED.")
    @ApiResponse(responseCode = "200", description = "Agreement rejected or returned to DRAFT")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SECRETARY', 'COORDINATOR', 'ADMIN')")
    public ResponseEntity<AgreementResponse> rejectAgreement(
            @PathVariable Long id,
            @Valid @RequestBody RejectAgreementRequest request,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.ok(agreementService.rejectAgreement(id, request.reason(), currentUser));
    }

    /**
     * Endorses a coordination-reviewed agreement, generating the PDF and sending
     * it to Documenso for digital signing (COORDINATION_REVIEW → PENDING_SIGNATURE).
     *
     * @param id          Agreement unique identifier.
     * @param currentUser The authenticated user (must be COORDINATOR or ADMIN).
     * @return 200 OK with the updated agreement in PENDING_SIGNATURE status.
     */
    @Operation(summary = "Endorse agreement",
            description = "Transitions COORDINATION_REVIEW → PENDING_SIGNATURE, generates PDF and sends to Documenso")
    @ApiResponse(responseCode = "200", description = "Agreement sent for digital signing")
    @PostMapping("/{id}/endorse")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<AgreementResponse> endorseAgreement(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.ok(agreementService.endorseAgreement(id, currentUser));
    }

    @Operation(summary = "Start evaluation",
            description = "Transitions ACTIVE → EVALUATION. Opens the grading phase for advisor and company tutor.")
    @ApiResponse(responseCode = "200", description = "Agreement moved to evaluation phase")
    @PostMapping("/{id}/start-evaluation")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<AgreementResponse> startEvaluation(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.ok(agreementService.startEvaluation(id, currentUser));
    }

    // ── Visits ────────────────────────────────────────────────────────────────

    @Operation(summary = "List visits", description = "Returns all advisor visits for an agreement")
    @ApiResponse(responseCode = "200", description = "Visits listed")
    @GetMapping("/{id}/visits")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ACADEMIC_ADVISOR', 'ADMIN')")
    public ResponseEntity<List<VisitResponse>> listVisits(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.ok(visitService.listVisits(id, currentUser));
    }

    @Operation(summary = "Register visit", description = "Records a new advisor visit for an ACTIVE agreement")
    @ApiResponse(responseCode = "201", description = "Visit registered")
    @PostMapping("/{id}/visits")
    @PreAuthorize("hasRole('ACADEMIC_ADVISOR')")
    public ResponseEntity<VisitResponse> registerVisit(
            @PathVariable Long id,
            @Valid @RequestBody CreateVisitRequest request,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(visitService.registerVisit(id, request, currentUser));
    }

    // ── Grading ───────────────────────────────────────────────────────────────

    @Operation(summary = "Submit grade", description = "Submits the advisor or tutor grade (0.0–5.0) for an agreement in EVALUATION. Auto-transitions to FINISHED when both grades are submitted.")
    @ApiResponse(responseCode = "200", description = "Grade recorded")
    @PutMapping("/{id}/grade")
    @PreAuthorize("hasAnyRole('ACADEMIC_ADVISOR', 'COMPANY_TUTOR')")
    public ResponseEntity<AgreementResponse> gradeAgreement(
            @PathVariable Long id,
            @Valid @RequestBody GradeRequest request,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.ok(agreementService.gradeAgreement(id, request, currentUser));
    }

    // ── Document upload ───────────────────────────────────────────────────────

    @Operation(summary = "Upload document",
            description = "Uploads a document to Cloudflare R2. STUDENT uploads CV (DRAFT) and CONTRACT/NATIONAL_ID/EPS/ARL/WORK_PLAN (PENDING_SIGNATURE). COMPANY_TUTOR uploads NIT/RUT/CAMARA_COMERCIO (DRAFT).")
    @ApiResponse(responseCode = "200", description = "Document uploaded")
    @PostMapping(value = "/{id}/documents/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STUDENT', 'COMPANY_TUTOR')")
    public ResponseEntity<AgreementResponse> uploadDocument(
            @PathVariable Long id,
            @PathVariable DocumentType type,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtUser currentUser) throws IOException {

        return ResponseEntity.ok(agreementService.uploadDocument(id, type, file, currentUser));
    }

    @Operation(summary = "List uploaded documents",
            description = "Returns the metadata of every document uploaded against the agreement. "
                    + "Use GET /{id}/documents/{type} to download the bytes. "
                    + "Authorization is enforced per-agreement in the service layer.")
    @ApiResponse(responseCode = "200", description = "Document metadata")
    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('STUDENT', 'COMPANY_TUTOR', 'ACADEMIC_ADVISOR', 'COORDINATOR', 'SECRETARY', 'ADMIN')")
    public ResponseEntity<List<AgreementDocumentResponse>> listDocuments(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {
        return ResponseEntity.ok(agreementService.listDocuments(id, currentUser));
    }

    @Operation(summary = "Download uploaded document",
            description = "Downloads a document previously uploaded to R2 (CV, NIT, RUT, CONTRACT, etc.). "
                    + "Authorization is enforced per-agreement in the service layer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File returned"),
            @ApiResponse(responseCode = "404", description = "Document not yet uploaded")
    })
    @GetMapping("/{id}/documents/{type}")
    @PreAuthorize("hasAnyRole('STUDENT', 'COMPANY_TUTOR', 'ACADEMIC_ADVISOR', 'COORDINATOR', 'SECRETARY', 'ADMIN')")
    public ResponseEntity<byte[]> downloadUploadedDocument(
            @PathVariable Long id,
            @PathVariable DocumentType type,
            @AuthenticationPrincipal JwtUser currentUser) {

        byte[] data = agreementService.downloadDocument(id, type, currentUser);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(type.name().toLowerCase() + "_" + id + ".pdf")
                .build());

        return ResponseEntity.ok().headers(headers).body(data);
    }

    @Operation(summary = "Download signed document",
            description = "Proxies the signed PDF from Documenso for the given agreement")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF returned"),
            @ApiResponse(responseCode = "404", description = "Agreement not found or not yet signed")
    })
    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {

        var agreement = agreementService.getAgreement(id, currentUser);

        if (agreement.documensoDocumentId() == null) {
            throw new IllegalStateException("Agreement has not been sent for signing yet");
        }

        byte[] pdf = documensoService.downloadSignedPdf(agreement.documensoDocumentId());

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("convenio_practica_" + id + ".pdf")
                .build());

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    // ── Certificate of completion ────────────────────────────────────────────

    @Operation(summary = "Approve certificate of completion",
            description = "Marks the constancia de culminación as approved so it can be "
                    + "downloaded by the participants. Only COORDINATOR or ADMIN may approve, "
                    + "and only when the agreement is FINISHED and not already approved.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agreement with approval fields populated"),
            @ApiResponse(responseCode = "403", description = "Role not allowed or cross-tenant"),
            @ApiResponse(responseCode = "409", description = "Agreement not FINISHED or already approved"),
            @ApiResponse(responseCode = "404", description = "Agreement not found")
    })
    @PostMapping("/{id}/approve-certificate")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<AgreementResponse> approveCertificate(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {
        return ResponseEntity.ok(agreementService.approveCertificate(id, currentUser));
    }

    @Operation(summary = "Download certificate of completion",
            description = "Issues the 'Constancia de Culminación' for a FINISHED agreement. "
                    + "Generated once per agreement and cached in R2. Accessible to the owning "
                    + "student, the assigned advisor/tutor, coordinators of the same university, "
                    + "and ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF returned"),
            @ApiResponse(responseCode = "403", description = "Not authorized for this agreement"),
            @ApiResponse(responseCode = "409", description = "Agreement is not in FINISHED status"),
            @ApiResponse(responseCode = "404", description = "Agreement not found")
    })
    @GetMapping("/{id}/certificate")
    @PreAuthorize("hasAnyRole('STUDENT', 'ACADEMIC_ADVISOR', 'COMPANY_TUTOR', 'COORDINATOR', 'ADMIN')")
    public ResponseEntity<byte[]> downloadCertificate(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtUser currentUser) {

        byte[] pdf = agreementService.downloadCertificate(id, currentUser);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("constancia_" + id + ".pdf")
                .build());

        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
