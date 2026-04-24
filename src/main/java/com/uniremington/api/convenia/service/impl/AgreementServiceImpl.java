package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.model.dto.*;
import com.uniremington.api.convenia.model.entity.*;
import com.uniremington.api.convenia.model.mapper.AgreementMapper;
import com.uniremington.api.convenia.model.vo.JwtUser;
import com.uniremington.api.convenia.repository.*;
import com.uniremington.api.convenia.service.AgreementService;
import com.uniremington.api.convenia.service.DocumensoService;
import com.uniremington.api.convenia.service.PdfGenerationService;
import com.uniremington.api.convenia.service.StorageService;
import com.uniremington.api.convenia.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Business logic implementation for the professional practice agreement lifecycle.
 *
 * <p>Enforces multi-tenancy: every operation verifies that the requesting user
 * belongs to the same university as the target agreement. ADMIN users
 * (universityId == null) bypass the tenant check and can access all universities.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgreementServiceImpl implements AgreementService {

    private final AgreementRepository               agreementRepository;
    private final AgreementDocumentRepository       agreementDocumentRepository;
    private final StudentRepository                 studentRepository;
    private final CompanyRepository                 companyRepository;
    private final UserRepository                    userRepository;
    private final AgreementStatusHistoryRepository  statusHistoryRepository;
    private final PracticeVisitRepository           practiceVisitRepository;
    private final AgreementMapper                   agreementMapper;
    private final PdfGenerationService              pdfGenerationService;
    private final DocumensoService                  documensoService;
    private final StorageService                    storageService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>STUDENT users must create agreements for their own student profile.
     * The agreement's university is derived from the student entity.</p>
     */
    @Override
    @Transactional
    public AgreementResponse createAgreement(CreateAgreementRequest request, JwtUser currentUser) {
        var student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student", request.studentId()));

        assertUniversityMatch(student.getUniversity().getId(), currentUser
        );

        if ("STUDENT".equals(currentUser.getRole()) &&
                !student.getUser().getId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Students can only create agreements for themselves");
        }

        var company = loadCompany(request.companyId(), student.getUniversity().getId());

        validateDates(request.startDate(), request.endDate(), request.contractType());
        validateWeeklyHours(request.weeklyHours());

        var agreement = Agreement.builder()
                .university(student.getUniversity())
                .student(student)
                .company(company)
                .practiceModality(request.practiceModality())
                .practiceComponent(request.practiceComponent())
                .contractType(request.contractType())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .weeklyHours(request.weeklyHours())
                .monthlyStipend(request.monthlyStipend())
                .status(AgreementStatus.DRAFT)
                .build();

        if (request.academicAdvisorId() != null) {
            agreement.setAcademicAdvisor(loadUser(request.academicAdvisorId(), UserRole.ACADEMIC_ADVISOR));
        }
        if (request.companyRepId() != null) {
            agreement.setCompanyRep(loadUser(request.companyRepId(), UserRole.COMPANY_TUTOR));
        }

        var saved = agreementRepository.save(agreement);
        recordStatusChange(saved, null, AgreementStatus.DRAFT, currentUser.getUserId(), null);
        log.info("Agreement created: id={}, student={}, university={}",
                saved.getId(), student.getId(), student.getUniversity().getId());
        return agreementMapper.toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AgreementResponse getAgreement(Long id, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertAgreementReadAccess(agreement, currentUser);
        return agreementMapper.toResponse(agreement);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AgreementSummaryResponse> listAgreements(JwtUser currentUser) {
        List<Agreement> agreements = switch (currentUser.getRole()) {
            case "STUDENT" -> {
                var student = studentRepository.findByUserId(currentUser.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "No student profile found for current user"));
                yield agreementRepository.findByStudentIdOrderByCreatedAtDesc(student.getId());
            }
            case "ACADEMIC_ADVISOR" ->
                    agreementRepository.findByAcademicAdvisorIdOrderByCreatedAtDesc(currentUser.getUserId());
            case "COMPANY_TUTOR" ->
                    agreementRepository.findByCompanyRepIdOrderByCreatedAtDesc(currentUser.getUserId());
            case "ADMIN" -> currentUser.getUniversityId() == null
                    ? agreementRepository.findAll()
                    : agreementRepository.findByUniversityIdOrderByCreatedAtDesc(currentUser.getUniversityId());
            // COORDINATOR and SECRETARY see the full tenant — they validate and coordinate across agreements.
            default -> agreementRepository.findByUniversityIdOrderByCreatedAtDesc(currentUser.getUniversityId());
        };

        return agreements.stream()
                .map(agreementMapper::toSummary)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only non-null fields in the request are applied (partial update semantics).</p>
     */
    @Override
    @Transactional
    public AgreementResponse updateAgreement(Long id, UpdateAgreementRequest request, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertTenantAccess(agreement, currentUser);
        assertOwnershipIfStudent(agreement, currentUser);
        assertStatus(agreement, AgreementStatus.DRAFT, "Only DRAFT agreements can be updated");

        if (request.companyId() != null) {
            agreement.setCompany(loadCompany(request.companyId(), agreement.getUniversity().getId()));
        }
        if (request.academicAdvisorId() != null) {
            agreement.setAcademicAdvisor(loadUser(request.academicAdvisorId(), UserRole.ACADEMIC_ADVISOR));
        }
        if (request.companyRepId() != null) {
            agreement.setCompanyRep(loadUser(request.companyRepId(), UserRole.COMPANY_TUTOR));
        }
        if (request.practiceModality() != null)  agreement.setPracticeModality(request.practiceModality());
        if (request.practiceComponent() != null) agreement.setPracticeComponent(request.practiceComponent());
        if (request.contractType() != null)       agreement.setContractType(request.contractType());
        if (request.startDate() != null)          agreement.setStartDate(request.startDate());
        if (request.endDate() != null)            agreement.setEndDate(request.endDate());
        if (request.weeklyHours() != null)        agreement.setWeeklyHours(request.weeklyHours());
        if (request.monthlyStipend() != null)     agreement.setMonthlyStipend(request.monthlyStipend());

        validateDates(agreement.getStartDate(), agreement.getEndDate(), agreement.getContractType());

        return agreementMapper.toResponse(agreementRepository.save(agreement));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteAgreement(Long id, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertTenantAccess(agreement, currentUser);
        assertOwnershipIfStudent(agreement, currentUser);
        assertStatus(agreement, AgreementStatus.DRAFT, "Only DRAFT agreements can be deleted");
        agreementRepository.delete(agreement);
        log.info("Agreement deleted: id={}", id);
    }

    // ── Workflow transitions ───────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AgreementResponse submitForReview(Long id, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertTenantAccess(agreement, currentUser);
        assertOwnershipIfStudent(agreement, currentUser);
        assertStatus(agreement, AgreementStatus.DRAFT, "Only DRAFT agreements can be submitted for review");

        if (agreement.getAcademicAdvisor() == null) {
            throw new IllegalArgumentException("Academic advisor must be assigned before submission");
        }
        if (agreement.getCompanyRep() == null) {
            throw new IllegalArgumentException("Company representative must be assigned before submission");
        }

        validateDates(agreement.getStartDate(), agreement.getEndDate(), agreement.getContractType());
        validateStudentEligibility(agreement.getStudent());

        agreement.setRejectionReason(null);
        agreement.setStatus(AgreementStatus.ADMIN_REVIEW);
        var saved = agreementRepository.save(agreement);
        recordStatusChange(saved, AgreementStatus.DRAFT, AgreementStatus.ADMIN_REVIEW, currentUser.getUserId(), null);
        log.info("Agreement id={} submitted for admin review", id);
        return agreementMapper.toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AgreementResponse approveAdminReview(Long id, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertTenantAccess(agreement, currentUser);
        assertStatus(agreement, AgreementStatus.ADMIN_REVIEW,
                "Only agreements in ADMIN_REVIEW can be approved at this stage");

        agreement.setStatus(AgreementStatus.COORDINATION_REVIEW);
        var saved = agreementRepository.save(agreement);
        recordStatusChange(saved, AgreementStatus.ADMIN_REVIEW, AgreementStatus.COORDINATION_REVIEW, currentUser.getUserId(), null);
        log.info("Agreement id={} advanced to coordination review", id);
        return agreementMapper.toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AgreementResponse rejectAgreement(Long id, String reason, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertTenantAccess(agreement, currentUser);

        var previousStatus = agreement.getStatus();

        if (previousStatus == AgreementStatus.ADMIN_REVIEW) {
            // Administrative rejection: return to DRAFT so the student can correct documents.
            agreement.setStatus(AgreementStatus.DRAFT);
            agreement.setRejectionReason(reason);
            var saved = agreementRepository.save(agreement);
            recordStatusChange(saved, AgreementStatus.ADMIN_REVIEW, AgreementStatus.DRAFT,
                    currentUser.getUserId(), reason);
            log.info("Agreement id={} returned to DRAFT from ADMIN_REVIEW: {}", id, reason);
            return agreementMapper.toResponse(saved);
        }

        if (previousStatus == AgreementStatus.COORDINATION_REVIEW) {
            // Academic rejection: terminal state.
            agreement.setStatus(AgreementStatus.REJECTED);
            agreement.setRejectionReason(reason);
            var saved = agreementRepository.save(agreement);
            recordStatusChange(saved, AgreementStatus.COORDINATION_REVIEW, AgreementStatus.REJECTED,
                    currentUser.getUserId(), reason);
            log.info("Agreement id={} rejected at COORDINATION_REVIEW: {}", id, reason);
            return agreementMapper.toResponse(saved);
        }

        throw new IllegalStateException(
                "Only agreements in ADMIN_REVIEW or COORDINATION_REVIEW can be rejected. Current status: "
                        + previousStatus);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method generates the PDF and sends it to Documenso within the same
     * transaction. If Documenso fails, the status is NOT updated (exception propagates).</p>
     */
    @Override
    @Transactional
    public AgreementResponse endorseAgreement(Long id, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertTenantAccess(agreement, currentUser);
        assertStatus(agreement, AgreementStatus.COORDINATION_REVIEW,
                "Only COORDINATION_REVIEW agreements can be endorsed for signing");

        byte[] pdfBytes = documensoService.isTemplateMode()
                ? null
                : pdfGenerationService.generateAgreementPdf(agreement);
        String documensoDocId = documensoService.sendForSignature(agreement, pdfBytes);

        agreement.setDocumensoDocumentId(documensoDocId);
        agreement.setStatus(AgreementStatus.PENDING_SIGNATURE);
        var saved = agreementRepository.save(agreement);
        recordStatusChange(saved, AgreementStatus.COORDINATION_REVIEW, AgreementStatus.PENDING_SIGNATURE, currentUser.getUserId(), null);
        log.info("Agreement id={} sent to Documenso, documentId={}", id, documensoDocId);
        return agreementMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The signed PDF URL is not available in the Documenso v2 webhook payload.
     * Retrieve it separately via {@code GET /envelope/download-item} using
     * {@link Agreement#getDocumensoDocumentId()} when the student requests download.</p>
     */
    @Override
    @Transactional
    public void activateAgreement(String documensoDocumentId) {
        var agreement = agreementRepository.findByDocumensoDocumentId(documensoDocumentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No agreement found for Documenso envelope id: " + documensoDocumentId));

        if (agreement.getStatus() != AgreementStatus.PENDING_SIGNATURE) {
            log.warn("Received DOCUMENT_COMPLETED for agreement id={} but status is {} — ignoring",
                    agreement.getId(), agreement.getStatus());
            agreementMapper.toResponse(agreement);
            return;
        }

        agreement.setStatus(AgreementStatus.ACTIVE);
        var saved = agreementRepository.save(agreement);
        recordStatusChange(saved, AgreementStatus.PENDING_SIGNATURE, AgreementStatus.ACTIVE, null,
                "Activated by Documenso webhook — all parties signed");
        log.info("Agreement id={} activated after all signatures completed", agreement.getId());
        agreementMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AgreementResponse startEvaluation(Long id, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertTenantAccess(agreement, currentUser);
        assertStatus(agreement, AgreementStatus.ACTIVE, "Only ACTIVE agreements can be moved to evaluation");

        long visitCount = practiceVisitRepository.countByAgreementId(id);
        if (visitCount < 3) {
            throw new IllegalStateException(
                    "At least 3 advisor visits must be registered before starting evaluation (found: "
                    + visitCount + ")");
        }

        agreement.setStatus(AgreementStatus.EVALUATION);
        var saved = agreementRepository.save(agreement);
        recordStatusChange(saved, AgreementStatus.ACTIVE, AgreementStatus.EVALUATION, currentUser.getUserId(), null);
        log.info("Agreement id={} moved to EVALUATION by user={}", id, currentUser.getUserId());
        return agreementMapper.toResponse(saved);
    }

    // ── Grade submission ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public AgreementResponse gradeAgreement(Long id, GradeRequest request, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertTenantAccess(agreement, currentUser);
        assertStatus(agreement, AgreementStatus.EVALUATION, "Only agreements in EVALUATION can be graded");

        switch (currentUser.getRole()) {
            case "ACADEMIC_ADVISOR" -> {
                if (agreement.getAcademicAdvisor() == null ||
                        !agreement.getAcademicAdvisor().getId().equals(currentUser.getUserId())) {
                    throw new AccessDeniedException("You are not the assigned academic advisor");
                }
                if (agreement.getAdvisorGrade() != null) {
                    throw new IllegalStateException("Advisor grade has already been submitted");
                }
                agreement.setAdvisorGrade(request.grade());
            }
            case "COMPANY_TUTOR" -> {
                if (agreement.getCompanyRep() == null ||
                        !agreement.getCompanyRep().getId().equals(currentUser.getUserId())) {
                    throw new AccessDeniedException("You are not the assigned company tutor");
                }
                if (agreement.getCompanyGrade() != null) {
                    throw new IllegalStateException("Company grade has already been submitted");
                }
                agreement.setCompanyGrade(request.grade());
            }
            default -> throw new AccessDeniedException("Only ACADEMIC_ADVISOR or COMPANY_TUTOR can grade agreements");
        }

        if (agreement.getAdvisorGrade() != null && agreement.getCompanyGrade() != null) {
            agreement.setFinalGrade(
                    agreement.getAdvisorGrade()
                            .add(agreement.getCompanyGrade())
                            .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP));
            agreement.setStatus(AgreementStatus.FINISHED);
            var saved = agreementRepository.save(agreement);
            recordStatusChange(saved, AgreementStatus.EVALUATION, AgreementStatus.FINISHED, currentUser.getUserId(),
                    "Final grade computed: " + agreement.getFinalGrade());
            log.info("Agreement id={} finished with finalGrade={}", id, agreement.getFinalGrade());
            return agreementMapper.toResponse(saved);
        }

        return agreementMapper.toResponse(agreementRepository.save(agreement));
    }

    // ── Document upload ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public AgreementResponse uploadDocument(Long id, DocumentType type, MultipartFile file,
                                            JwtUser currentUser) throws IOException {
        var agreement = loadAgreement(id);
        assertTenantAccess(agreement, currentUser);

        boolean isCompanyDoc = type == DocumentType.NIT
                || type == DocumentType.RUT
                || type == DocumentType.CAMARA_COMERCIO;

        if (isCompanyDoc) {
            if (!"COMPANY_TUTOR".equals(currentUser.getRole())) {
                throw new AccessDeniedException("Only COMPANY_TUTOR can upload company legal documents");
            }
            if (agreement.getCompanyRep() == null ||
                    !agreement.getCompanyRep().getId().equals(currentUser.getUserId())) {
                throw new AccessDeniedException("You are not the assigned company representative for this agreement");
            }
            assertStatus(agreement, AgreementStatus.DRAFT,
                    "Company documents (NIT, RUT, Cámara de Comercio) can only be uploaded while the agreement is in DRAFT");
        } else {
            assertOwnershipIfStudent(agreement, currentUser);
            if (type == DocumentType.CV) {
                assertStatus(agreement, AgreementStatus.DRAFT,
                        "CV can only be uploaded while the agreement is in DRAFT");
            } else {
                assertStatus(agreement, AgreementStatus.PENDING_SIGNATURE,
                        "Student documents (CONTRACT, NATIONAL_ID, EPS, ARL, WORK_PLAN) can only be uploaded during PENDING_SIGNATURE");
            }
        }

        String ext = "";
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String newKey = "agreements/" + id + "/" + type.name().toLowerCase() + "/" + UUID.randomUUID() + ext;

        // If a previous document of this type exists, remember its key so we
        // can delete the stale R2 object *after* the upsert succeeds. This
        // minimizes the window where we could end up with an orphan AND lose
        // the active one.
        String previousKey = agreementDocumentRepository
                .findByAgreementIdAndDocumentType(id, type)
                .map(AgreementDocument::getFileKey)
                .orElse(null);

        storageService.upload(newKey, file.getContentType(), file.getBytes());

        agreementDocumentRepository.upsert(
                id,
                type.name(),
                newKey,
                file.getContentType(),
                originalName,
                file.getSize(),
                currentUser.getUserId()
        );

        if (previousKey != null && !previousKey.equals(newKey)) {
            try {
                storageService.delete(previousKey);
            } catch (RuntimeException ex) {
                // Orphan in R2 is preferable to failing the request after the
                // new document is already persisted. Log and move on.
                log.warn("Failed to delete previous R2 object {} after re-upload of {} for agreement {}: {}",
                        previousKey, type, id, ex.getMessage());
            }
        }

        log.info("Document {} uploaded for agreement id={} by user={} (key={})",
                type, id, currentUser.getUserId(), newKey);
        return agreementMapper.toResponse(agreement);
    }

    // ── Document listing / download ───────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AgreementDocumentResponse> listDocuments(Long id, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertDocumentAccess(agreement, currentUser);

        return agreementDocumentRepository.findAllByAgreementId(id).stream()
                .map(doc -> new AgreementDocumentResponse(
                        doc.getId(),
                        doc.getDocumentType(),
                        doc.getContentType(),
                        doc.getOriginalName(),
                        doc.getFileSizeBytes(),
                        doc.getUploadedBy() != null ? doc.getUploadedBy().getId() : null,
                        doc.getUpdatedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadDocument(Long id, DocumentType type, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertDocumentAccess(agreement, currentUser);

        var document = agreementDocumentRepository
                .findByAgreementIdAndDocumentType(id, type)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document " + type.name() + " has not been uploaded for agreement " + id));
        return storageService.download(document.getFileKey());
    }

    // ── Certificate (Constancia de Culminación) ───────────────────────────────

    private static final String CERTIFICATE_CONTENT_TYPE = "application/pdf";
    private static final String CERTIFICATE_KEY_PREFIX   = "certificates/";

    /**
     * {@inheritDoc}
     *
     * <p><strong>Concurrency note (accepted MVP trade-off):</strong> two concurrent
     * first-time requests for the same agreement may both miss the cache, generate
     * two PDFs and upload twice to the same R2 key. The key is deterministic
     * ({@code certificates/{univId}/{agreementId}.pdf}) so last-write-wins leaves
     * a consistent cached object. TODO post-MVP: guard with
     * {@code @Lock(LockModeType.PESSIMISTIC_WRITE)} to avoid double render + upload.</p>
     */
    @Override
    @Transactional
    public AgreementResponse approveCertificate(Long id, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertTenantAccess(agreement, currentUser);
        assertStatus(agreement, AgreementStatus.FINISHED,
                "Certificate can only be approved once the agreement is FINISHED");

        if (agreement.getCertificateApprovedAt() != null) {
            throw new IllegalStateException(
                    "Certificate has already been approved for this agreement");
        }

        var approver = userRepository.getReferenceById(currentUser.getUserId());
        agreement.setCertificateApprovedBy(approver);
        agreement.setCertificateApprovedAt(java.time.LocalDateTime.now());
        var saved = agreementRepository.save(agreement);
        log.info("Certificate approved for agreement id={} by user={}", id, currentUser.getUserId());
        return agreementMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public byte[] downloadCertificate(Long id, JwtUser currentUser) {
        var agreement = loadAgreement(id);
        assertCertificateAccess(agreement, currentUser);
        assertStatus(agreement, AgreementStatus.FINISHED,
                "Certificate is only available once the agreement is FINISHED");

        if (agreement.getCertificateApprovedAt() == null) {
            throw new IllegalStateException(
                    "Certificate has not been approved by coordination yet");
        }

        if (agreement.getCertificateFileKey() != null) {
            return storageService.download(agreement.getCertificateFileKey());
        }

        byte[] pdf = pdfGenerationService.generateCertificatePdf(agreement);
        String key = CERTIFICATE_KEY_PREFIX
                + agreement.getUniversity().getId() + "/" + agreement.getId() + ".pdf";
        storageService.upload(key, CERTIFICATE_CONTENT_TYPE, pdf);
        agreement.setCertificateFileKey(key);
        agreementRepository.save(agreement);
        log.info("Certificate generated and cached for agreement id={}, key={}", id, key);
        return pdf;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Agreement loadAgreement(Long id) {
        return agreementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement", id));
    }

    private Company loadCompany(Long companyId, Long universityId) {
        return companyRepository.findByIdAndUniversityId(companyId, universityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Company not found with id " + companyId + " in your university"));
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private User loadUser(Long userId, UserRole expectedRole) {
        var user = loadUser(userId);
        if (user.getRole() != expectedRole) {
            throw new IllegalArgumentException(
                    "User " + userId + " must have role " + expectedRole.name()
                    + " but has " + user.getRole().name());
        }
        return user;
    }

    private void assertTenantAccess(Agreement agreement, JwtUser user) {
        if (user.getUniversityId() != null &&
                !user.getUniversityId().equals(agreement.getUniversity().getId())) {
            throw new AccessDeniedException("Agreement does not belong to your university");
        }
    }

    private void assertOwnershipIfStudent(Agreement agreement, JwtUser user) {
        if ("STUDENT".equals(user.getRole()) &&
                !agreement.getStudent().getUser().getId().equals(user.getUserId())) {
            throw new AccessDeniedException("You can only access your own agreements");
        }
    }

    /**
     * Per-role access control for reading a single agreement (detail view).
     *
     * <p>ADMIN bypasses tenancy. Other roles require tenant match plus:</p>
     * <ul>
     *   <li>COORDINATOR and SECRETARY — allowed (they coordinate / validate).</li>
     *   <li>STUDENT — only the owner.</li>
     *   <li>ACADEMIC_ADVISOR / COMPANY_TUTOR — only if assigned to this agreement.</li>
     *   <li>Any other role — denied.</li>
     * </ul>
     *
     * <p>Without this check, a non-assigned advisor/tutor in the same tenant can
     * read agreements that are not theirs (IDOR).</p>
     */
    private void assertAgreementReadAccess(Agreement agreement, JwtUser user) {
        if ("ADMIN".equals(user.getRole())) {
            return;
        }
        assertTenantAccess(agreement, user);

        switch (user.getRole()) {
            case "COORDINATOR", "SECRETARY" -> { /* allowed within tenant */ }
            case "STUDENT" -> {
                if (!agreement.getStudent().getUser().getId().equals(user.getUserId())) {
                    throw new AccessDeniedException("You can only access your own agreements");
                }
            }
            case "ACADEMIC_ADVISOR" -> {
                if (agreement.getAcademicAdvisor() == null ||
                        !agreement.getAcademicAdvisor().getId().equals(user.getUserId())) {
                    throw new AccessDeniedException("You are not the assigned advisor for this agreement");
                }
            }
            case "COMPANY_TUTOR" -> {
                if (agreement.getCompanyRep() == null ||
                        !agreement.getCompanyRep().getId().equals(user.getUserId())) {
                    throw new AccessDeniedException("You are not the assigned tutor for this agreement");
                }
            }
            default -> throw new AccessDeniedException(
                    "Role " + user.getRole() + " cannot access this agreement");
        }
    }

    /**
     * Per-role access control for reading uploaded documents (listing / download).
     *
     * <p>ADMIN bypasses tenancy. Other roles must be in the same tenant. Beyond
     * tenant:</p>
     * <ul>
     *   <li>COORDINATOR and SECRETARY — allowed (they review documents).</li>
     *   <li>STUDENT — only the agreement's owner.</li>
     *   <li>ACADEMIC_ADVISOR / COMPANY_TUTOR — only if assigned to this agreement.</li>
     *   <li>Any other role — denied.</li>
     * </ul>
     */
    private void assertDocumentAccess(Agreement agreement, JwtUser user) {
        if ("ADMIN".equals(user.getRole())) {
            return;
        }
        assertTenantAccess(agreement, user);

        switch (user.getRole()) {
            case "COORDINATOR", "SECRETARY" -> { /* allowed within tenant */ }
            case "STUDENT" -> {
                if (!agreement.getStudent().getUser().getId().equals(user.getUserId())) {
                    throw new AccessDeniedException("You can only access documents of your own agreements");
                }
            }
            case "ACADEMIC_ADVISOR" -> {
                if (agreement.getAcademicAdvisor() == null ||
                        !agreement.getAcademicAdvisor().getId().equals(user.getUserId())) {
                    throw new AccessDeniedException("You are not the assigned advisor for this agreement");
                }
            }
            case "COMPANY_TUTOR" -> {
                if (agreement.getCompanyRep() == null ||
                        !agreement.getCompanyRep().getId().equals(user.getUserId())) {
                    throw new AccessDeniedException("You are not the assigned tutor for this agreement");
                }
            }
            default -> throw new AccessDeniedException(
                    "Role " + user.getRole() + " cannot access agreement documents");
        }
    }

    /**
     * Per-role access control for the certificate of completion.
     *
     * <p>ADMIN bypasses tenancy; any other role must belong to the same university.
     * Beyond tenancy: STUDENT must own the agreement; ACADEMIC_ADVISOR and
     * COMPANY_TUTOR must be the ones assigned to the agreement; COORDINATOR
     * has access across its tenant. Every other role (e.g. SECRETARY) is denied.</p>
     */
    private void assertCertificateAccess(Agreement agreement, JwtUser user) {
        if ("ADMIN".equals(user.getRole())) {
            return;
        }
        assertTenantAccess(agreement, user);

        switch (user.getRole()) {
            case "COORDINATOR" -> { /* allowed within tenant */ }
            case "STUDENT" -> {
                if (!agreement.getStudent().getUser().getId().equals(user.getUserId())) {
                    throw new AccessDeniedException("You can only download your own certificate");
                }
            }
            case "ACADEMIC_ADVISOR" -> {
                if (agreement.getAcademicAdvisor() == null ||
                        !agreement.getAcademicAdvisor().getId().equals(user.getUserId())) {
                    throw new AccessDeniedException("You are not the assigned advisor for this agreement");
                }
            }
            case "COMPANY_TUTOR" -> {
                if (agreement.getCompanyRep() == null ||
                        !agreement.getCompanyRep().getId().equals(user.getUserId())) {
                    throw new AccessDeniedException("You are not the assigned tutor for this agreement");
                }
            }
            default -> throw new AccessDeniedException(
                    "Role " + user.getRole() + " cannot download the certificate");
        }
    }

    private void assertUniversityMatch(Long entityUniversityId, JwtUser user) {
        if (user.getUniversityId() != null && !user.getUniversityId().equals(entityUniversityId)) {
            throw new AccessDeniedException("Student does not belong to your university");
        }
    }

    private void assertStatus(Agreement agreement, AgreementStatus required, String message) {
        if (agreement.getStatus() != required) {
            throw new IllegalStateException(message + ". Current status: " + agreement.getStatus());
        }
    }

    /**
     * Validates that the student meets the credit and seminar requirements
     * defined in Resolución 002-2024 (Art. §1) before an agreement is submitted.
     *
     * <p>Skips the check if {@code totalCredits} or {@code approvedCredits} are
     * not yet populated (graceful degradation for legacy data).</p>
     */
    private void validateStudentEligibility(Student student) {
        var program = student.getAcademicProgram();

        if (program.getTotalCredits() != null && program.getTotalCredits() > 0) {
            double required = program.getProgramType() == ProgramType.PROFESSIONAL ? 0.80 : 0.70;
            double approved = (double) student.getApprovedCredits() / program.getTotalCredits();

            if (approved < required) {
                throw new IllegalArgumentException(String.format(
                        "Student has not met the credit requirement. Required: %.0f%%, current: %.1f%%",
                        required * 100, approved * 100));
            }
        }

        if (student.getSeminarsCompleted() < 2) {
            throw new IllegalArgumentException(
                    "Student must complete at least 2 mandatory practice seminars before submitting (completed: "
                            + student.getSeminarsCompleted() + ")");
        }
    }

    private void validateWeeklyHours(Integer weeklyHours) {
        if (weeklyHours == null) return;
        if (weeklyHours < 20 || weeklyHours > 48) {
            throw new IllegalArgumentException(
                    "Weekly hours must be between 20 and 48 (found: " + weeklyHours + ")");
        }
    }

    /**
     * Appends an immutable status-change record to the agreement audit trail.
     *
     * @param agreement      The agreement whose status changed.
     * @param from           The previous status (null for initial DRAFT creation).
     * @param to             The new status.
     * @param changedByUserId  User ID who triggered the change (null for system/webhook).
     * @param notes          Optional notes (e.g., rejection reason).
     */
    private void recordStatusChange(Agreement agreement, AgreementStatus from, AgreementStatus to,
                                    Long changedByUserId, String notes) {
        var history = AgreementStatusHistory.builder()
                .agreement(agreement)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(changedByUserId != null ? userRepository.getReferenceById(changedByUserId) : null)
                .notes(notes)
                .build();
        statusHistoryRepository.save(history);
    }

    private void validateDates(java.time.LocalDate startDate, java.time.LocalDate endDate,
                                ContractType contractType) {
        if (startDate == null || endDate == null) return;

        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        long months = ChronoUnit.MONTHS.between(startDate, endDate);

        if (contractType == ContractType.APPRENTICESHIP) {
            if (months != 6) {
                throw new IllegalArgumentException(
                        "APPRENTICESHIP contracts must be exactly 6 months (found: " + months + ")");
            }
        } else {
            if (months < 4) {
                throw new IllegalArgumentException(
                        "Practice duration must be at least 4 months (found: " + months + ")");
            }
            if (months > 12) {
                throw new IllegalArgumentException(
                        "Practice duration must not exceed 12 months (found: " + months + ")");
            }
        }
    }
}
