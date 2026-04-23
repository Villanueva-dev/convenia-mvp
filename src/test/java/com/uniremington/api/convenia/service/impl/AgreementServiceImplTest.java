package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.model.dto.GradeRequest;
import com.uniremington.api.convenia.model.entity.*;
import com.uniremington.api.convenia.model.mapper.AgreementMapper;
import com.uniremington.api.convenia.model.vo.JwtUser;
import com.uniremington.api.convenia.repository.*;
import com.uniremington.api.convenia.service.DocumensoService;
import com.uniremington.api.convenia.service.PdfGenerationService;
import com.uniremington.api.convenia.service.StorageService;
import com.uniremington.api.convenia.shared.exception.ResourceNotFoundException;
import com.uniremington.api.convenia.shared.exception.ExternalServiceException;
import com.uniremington.api.convenia.util.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgreementServiceImplTest {

    @Mock AgreementRepository               agreementRepository;
    @Mock AgreementDocumentRepository       agreementDocumentRepository;
    @Mock StudentRepository                 studentRepository;
    @Mock CompanyRepository                 companyRepository;
    @Mock UserRepository                    userRepository;
    @Mock AgreementStatusHistoryRepository  statusHistoryRepository;
    @Mock PracticeVisitRepository           practiceVisitRepository;
    @Mock AgreementMapper                   agreementMapper;
    @Mock PdfGenerationService              pdfGenerationService;
    @Mock DocumensoService                  documensoService;
    @Mock StorageService                    storageService;

    @InjectMocks AgreementServiceImpl service;

    University university;
    User       advisor;
    User       tutor;
    User       studentUser;
    Student    student;
    Company    company;

    @BeforeEach
    void setUp() {
        university  = TestFixtures.university(1L);
        advisor     = TestFixtures.user(10L, UserRole.ACADEMIC_ADVISOR, university);
        tutor       = TestFixtures.user(20L, UserRole.COMPANY_TUTOR, university);
        studentUser = TestFixtures.user(30L, UserRole.STUDENT, university);
        student     = TestFixtures.student(1L, studentUser, university);
        company     = TestFixtures.company(1L, university);
    }

    // ── gradeAgreement ────────────────────────────────────────────────────────

    @Nested
    class GradeAgreement {

        @Test
        void advisorSetsGradeSuccessfully() {
            var agreement   = evaluationAgreement();
            var request     = new GradeRequest(new BigDecimal("4.5"));
            var currentUser = TestFixtures.jwtUser(10L, "ACADEMIC_ADVISOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(agreementMapper.toResponse(any())).thenReturn(TestFixtures.dummyAgreementResponse());

            service.gradeAgreement(1L, request, currentUser);

            assertThat(agreement.getAdvisorGrade()).isEqualByComparingTo("4.5");
            assertThat(agreement.getFinalGrade()).isNull();
        }

        @Test
        void tutorSetsGradeSuccessfully() {
            var agreement   = evaluationAgreement();
            var request     = new GradeRequest(new BigDecimal("3.5"));
            var currentUser = TestFixtures.jwtUser(20L, "COMPANY_TUTOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(agreementMapper.toResponse(any())).thenReturn(TestFixtures.dummyAgreementResponse());

            service.gradeAgreement(1L, request, currentUser);

            assertThat(agreement.getCompanyGrade()).isEqualByComparingTo("3.5");
            assertThat(agreement.getFinalGrade()).isNull();
        }

        @Test
        void computesFinalGradeAndTransitionsToFinished() {
            var agreement = evaluationAgreement();
            agreement.setAdvisorGrade(new BigDecimal("4.0"));

            var request     = new GradeRequest(new BigDecimal("3.0"));
            var currentUser = TestFixtures.jwtUser(20L, "COMPANY_TUTOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.getReferenceById(20L)).thenReturn(tutor);
            when(agreementMapper.toResponse(any())).thenReturn(TestFixtures.dummyAgreementResponse());

            service.gradeAgreement(1L, request, currentUser);

            assertThat(agreement.getFinalGrade()).isEqualByComparingTo("3.5");
            assertThat(agreement.getStatus()).isEqualTo(AgreementStatus.FINISHED);
        }

        @Test
        void throwsWhenAgreementNotInEvaluation() {
            var agreement = evaluationAgreement();
            agreement.setStatus(AgreementStatus.ACTIVE);
            var currentUser = TestFixtures.jwtUser(10L, "ACADEMIC_ADVISOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.gradeAgreement(1L, new GradeRequest(new BigDecimal("4.0")), currentUser))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void throwsWhenAdvisorNotAssignedToAgreement() {
            var agreement   = evaluationAgreement();
            var otherUser   = TestFixtures.jwtUser(99L, "ACADEMIC_ADVISOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.gradeAgreement(1L, new GradeRequest(new BigDecimal("4.0")), otherUser))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void throwsWhenAdvisorGradeAlreadySubmitted() {
            var agreement = evaluationAgreement();
            agreement.setAdvisorGrade(new BigDecimal("4.0"));
            var currentUser = TestFixtures.jwtUser(10L, "ACADEMIC_ADVISOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.gradeAgreement(1L, new GradeRequest(new BigDecimal("3.5")), currentUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been submitted");
        }

        @Test
        void throwsWhenTutorGradeAlreadySubmitted() {
            var agreement = evaluationAgreement();
            agreement.setCompanyGrade(new BigDecimal("3.0"));
            var currentUser = TestFixtures.jwtUser(20L, "COMPANY_TUTOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.gradeAgreement(1L, new GradeRequest(new BigDecimal("4.0")), currentUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been submitted");
        }

        @Test
        void throwsWhenUnauthorizedRoleTries() {
            var agreement   = evaluationAgreement();
            var coordinator = TestFixtures.jwtUser(1L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.gradeAgreement(1L, new GradeRequest(new BigDecimal("4.0")), coordinator))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ── submitForReview ───────────────────────────────────────────────────────

    @Nested
    class SubmitForReview {

        @Test
        void transitionsDraftToAdminReview() {
            var agreement = activeAgreement();
            agreement.setStatus(AgreementStatus.DRAFT);
            student.setSeminarsCompleted(2);
            student.setApprovedCredits(130);

            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.getReferenceById(30L)).thenReturn(studentUser);
            when(agreementMapper.toResponse(any())).thenReturn(TestFixtures.dummyAgreementResponse());

            service.submitForReview(1L, currentUser);

            assertThat(agreement.getStatus()).isEqualTo(AgreementStatus.ADMIN_REVIEW);
        }

        @Test
        void throwsWhenAgreementNotDraft() {
            var agreement   = activeAgreement();
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.submitForReview(1L, currentUser))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void throwsWhenAcademicAdvisorMissing() {
            var agreement = activeAgreement();
            agreement.setStatus(AgreementStatus.DRAFT);
            agreement.setAcademicAdvisor(null);
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.submitForReview(1L, currentUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("advisor");
        }

        @Test
        void throwsWhenCompanyRepMissing() {
            var agreement = activeAgreement();
            agreement.setStatus(AgreementStatus.DRAFT);
            agreement.setCompanyRep(null);
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.submitForReview(1L, currentUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("representative");
        }
    }

    // ── startEvaluation ───────────────────────────────────────────────────────

    @Nested
    class StartEvaluation {

        @Test
        void transitionsActiveToEvaluationWithEnoughVisits() {
            var agreement   = activeAgreement();
            var currentUser = TestFixtures.jwtUser(1L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(practiceVisitRepository.countByAgreementId(1L)).thenReturn(3L);
            when(agreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.getReferenceById(1L)).thenReturn(advisor);
            when(agreementMapper.toResponse(any())).thenReturn(TestFixtures.dummyAgreementResponse());

            service.startEvaluation(1L, currentUser);

            assertThat(agreement.getStatus()).isEqualTo(AgreementStatus.EVALUATION);
        }

        @Test
        void throwsWhenFewerThanThreeVisits() {
            var agreement   = activeAgreement();
            var currentUser = TestFixtures.jwtUser(1L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(practiceVisitRepository.countByAgreementId(1L)).thenReturn(2L);

            assertThatThrownBy(() -> service.startEvaluation(1L, currentUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("3 advisor visits");
        }

        @Test
        void throwsWhenAgreementNotActive() {
            var agreement   = activeAgreement();
            agreement.setStatus(AgreementStatus.DRAFT);
            var currentUser = TestFixtures.jwtUser(1L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.startEvaluation(1L, currentUser))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ── rejectAgreement ───────────────────────────────────────────────────────

    @Nested
    class RejectAgreement {

        @Test
        void adminReviewRejectionReturnsAgreementToDraft() {
            var agreement = activeAgreement();
            agreement.setStatus(AgreementStatus.ADMIN_REVIEW);
            var currentUser = TestFixtures.jwtUser(1L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.getReferenceById(1L)).thenReturn(advisor);
            when(agreementMapper.toResponse(any())).thenReturn(TestFixtures.dummyAgreementResponse());

            service.rejectAgreement(1L, "Documentos incompletos", currentUser);

            assertThat(agreement.getStatus()).isEqualTo(AgreementStatus.DRAFT);
            assertThat(agreement.getRejectionReason()).isEqualTo("Documentos incompletos");
        }

        @Test
        void coordinationReviewRejectionIsTerminal() {
            var agreement = activeAgreement();
            agreement.setStatus(AgreementStatus.COORDINATION_REVIEW);
            var currentUser = TestFixtures.jwtUser(1L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.getReferenceById(1L)).thenReturn(advisor);
            when(agreementMapper.toResponse(any())).thenReturn(TestFixtures.dummyAgreementResponse());

            service.rejectAgreement(1L, "No cumple requisitos académicos", currentUser);

            assertThat(agreement.getStatus()).isEqualTo(AgreementStatus.REJECTED);
            assertThat(agreement.getRejectionReason()).isEqualTo("No cumple requisitos académicos");
        }

        @Test
        void throwsWhenStatusNotReviewable() {
            var agreement   = activeAgreement();
            var currentUser = TestFixtures.jwtUser(1L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.rejectAgreement(1L, "reason", currentUser))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ── tenant isolation ──────────────────────────────────────────────────────

    @Nested
    class TenantIsolation {

        @Test
        void throwsWhenAccessingAgreementFromDifferentUniversity() {
            var agreement         = activeAgreement();
            var differentTenant   = TestFixtures.jwtUser(99L, "COORDINATOR", 2L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.getAgreement(1L, differentTenant))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void adminBypassesTenantCheck() {
            var agreement = activeAgreement();
            var admin     = TestFixtures.jwtUser(1L, "ADMIN", null);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementMapper.toResponse(any())).thenReturn(TestFixtures.dummyAgreementResponse());

            assertThatCode(() -> service.getAgreement(1L, admin)).doesNotThrowAnyException();
        }

        @Test
        void studentCanOnlyAccessOwnAgreement() {
            var agreement      = activeAgreement();
            var otherStudent   = TestFixtures.jwtUser(99L, "STUDENT", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.getAgreement(1L, otherStudent))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ── not found ─────────────────────────────────────────────────────────────

    @Test
    void throwsResourceNotFoundWhenAgreementMissing() {
        when(agreementRepository.findById(999L)).thenReturn(Optional.empty());
        var currentUser = TestFixtures.jwtUser(1L, "COORDINATOR", 1L);

        assertThatThrownBy(() -> service.getAgreement(999L, currentUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── downloadCertificate ───────────────────────────────────────────────────

    @Nested
    class DownloadCertificate {

        @Test
        void generatesAndCachesOnFirstCall() {
            var agreement = finishedAgreement();
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);

            byte[] generatedPdf = "pdf-bytes".getBytes();
            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(pdfGenerationService.generateCertificatePdf(agreement)).thenReturn(generatedPdf);
            when(agreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            byte[] result = service.downloadCertificate(1L, currentUser);

            assertThat(result).isEqualTo(generatedPdf);
            assertThat(agreement.getCertificateFileKey()).isEqualTo("certificates/1/1.pdf");

            var keyCaptor  = ArgumentCaptor.forClass(String.class);
            var typeCaptor = ArgumentCaptor.forClass(String.class);
            var dataCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(storageService).upload(keyCaptor.capture(), typeCaptor.capture(), dataCaptor.capture());
            assertThat(keyCaptor.getValue()).isEqualTo("certificates/1/1.pdf");
            assertThat(typeCaptor.getValue()).isEqualTo("application/pdf");
            assertThat(dataCaptor.getValue()).isSameAs(generatedPdf);

            verify(storageService, never()).download(anyString());
        }

        @Test
        void uploadFailureDoesNotPersistFileKey() {
            var agreement = finishedAgreement();
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(pdfGenerationService.generateCertificatePdf(agreement)).thenReturn("pdf".getBytes());
            when(storageService.upload(anyString(), anyString(), any(byte[].class)))
                    .thenThrow(new ExternalServiceException("R2 upload failed", null));

            assertThatThrownBy(() -> service.downloadCertificate(1L, currentUser))
                    .isInstanceOf(ExternalServiceException.class);

            assertThat(agreement.getCertificateFileKey()).isNull();
            verify(agreementRepository, never()).save(any());
        }

        @Test
        void coordinatorOfSameTenantCanDownload() {
            var agreement = finishedAgreement();
            agreement.setCertificateFileKey("certificates/1/1.pdf");
            var coordinator = TestFixtures.jwtUser(5L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(storageService.download("certificates/1/1.pdf")).thenReturn("cached".getBytes());

            assertThatCode(() -> service.downloadCertificate(1L, coordinator))
                    .doesNotThrowAnyException();
        }

        @Test
        void servesFromCacheWhenFileKeyPresent() {
            var agreement = finishedAgreement();
            agreement.setCertificateFileKey("certificates/1/1.pdf");
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);

            byte[] cached = "cached-pdf".getBytes();
            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(storageService.download("certificates/1/1.pdf")).thenReturn(cached);

            byte[] result = service.downloadCertificate(1L, currentUser);

            assertThat(result).isEqualTo(cached);
            verify(pdfGenerationService, never()).generateCertificatePdf(any());
            verify(storageService, never()).upload(anyString(), anyString(), any());
        }

        @Test
        void throwsWhenAgreementNotFinished() {
            var agreement = finishedAgreement();
            agreement.setStatus(AgreementStatus.ACTIVE);
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.downloadCertificate(1L, currentUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FINISHED");
        }

        @Test
        void deniesStudentWhoIsNotOwner() {
            var agreement    = finishedAgreement();
            var otherStudent = TestFixtures.jwtUser(99L, "STUDENT", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.downloadCertificate(1L, otherStudent))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void deniesAdvisorNotAssignedToAgreement() {
            var agreement   = finishedAgreement();
            var otherAdvisor = TestFixtures.jwtUser(99L, "ACADEMIC_ADVISOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.downloadCertificate(1L, otherAdvisor))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void deniesTutorNotAssignedToAgreement() {
            var agreement  = finishedAgreement();
            var otherTutor = TestFixtures.jwtUser(99L, "COMPANY_TUTOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.downloadCertificate(1L, otherTutor))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void deniesCrossTenantAccess() {
            var agreement = finishedAgreement();
            var foreign   = TestFixtures.jwtUser(1L, "COORDINATOR", 2L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.downloadCertificate(1L, foreign))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void allowsAdminAcrossTenants() {
            var agreement = finishedAgreement();
            agreement.setCertificateFileKey("certificates/1/1.pdf");
            var admin = TestFixtures.jwtUser(1L, "ADMIN", null);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(storageService.download("certificates/1/1.pdf")).thenReturn("pdf".getBytes());

            assertThatCode(() -> service.downloadCertificate(1L, admin)).doesNotThrowAnyException();
        }

        @Test
        void deniesSecretary() {
            var agreement = finishedAgreement();
            var secretary = TestFixtures.jwtUser(77L, "SECRETARY", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.downloadCertificate(1L, secretary))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ── uploadDocument ────────────────────────────────────────────────────────

    @Nested
    class UploadDocument {

        @Test
        void studentUploadsCvOnDraftAgreement() throws Exception {
            var agreement   = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
            agreement.setStatus(AgreementStatus.DRAFT);
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);
            var file        = new org.springframework.mock.web.MockMultipartFile(
                    "file", "cv.pdf", "application/pdf", "bytes".getBytes());

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementDocumentRepository.findByAgreementIdAndDocumentType(1L, DocumentType.CV))
                    .thenReturn(Optional.empty());
            when(agreementMapper.toResponse(agreement)).thenReturn(TestFixtures.dummyAgreementResponse());

            service.uploadDocument(1L, DocumentType.CV, file, currentUser);

            var keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(storageService).upload(keyCaptor.capture(), eq("application/pdf"), eq("bytes".getBytes()));
            assertThat(keyCaptor.getValue()).startsWith("agreements/1/cv/").endsWith(".pdf");

            verify(agreementDocumentRepository).upsert(
                    eq(1L), eq("CV"), eq(keyCaptor.getValue()),
                    eq("application/pdf"), eq("cv.pdf"), eq(5L), eq(30L));
            verify(storageService, never()).delete(anyString());
        }

        @Test
        void reuploadReplacesPreviousFileAndDeletesOldR2Object() throws Exception {
            var agreement   = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
            agreement.setStatus(AgreementStatus.DRAFT);
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);
            var file        = new org.springframework.mock.web.MockMultipartFile(
                    "file", "new-cv.pdf", "application/pdf", "new".getBytes());

            var previousDoc = AgreementDocument.builder()
                    .id(77L).agreement(agreement).documentType(DocumentType.CV)
                    .fileKey("agreements/1/cv/old-uuid.pdf")
                    .uploadedBy(studentUser)
                    .build();

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementDocumentRepository.findByAgreementIdAndDocumentType(1L, DocumentType.CV))
                    .thenReturn(Optional.of(previousDoc));
            when(agreementMapper.toResponse(agreement)).thenReturn(TestFixtures.dummyAgreementResponse());

            service.uploadDocument(1L, DocumentType.CV, file, currentUser);

            verify(storageService).upload(anyString(), eq("application/pdf"), any(byte[].class));
            verify(agreementDocumentRepository).upsert(
                    eq(1L), eq("CV"), anyString(), anyString(), anyString(), anyLong(), eq(30L));
            verify(storageService).delete("agreements/1/cv/old-uuid.pdf");
        }

        @Test
        void orphanDeleteFailureDoesNotBreakUpload() throws Exception {
            var agreement   = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
            agreement.setStatus(AgreementStatus.DRAFT);
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);
            var file        = new org.springframework.mock.web.MockMultipartFile(
                    "file", "x.pdf", "application/pdf", "x".getBytes());

            var previousDoc = AgreementDocument.builder()
                    .id(77L).fileKey("agreements/1/cv/old.pdf")
                    .documentType(DocumentType.CV).agreement(agreement).uploadedBy(studentUser).build();

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementDocumentRepository.findByAgreementIdAndDocumentType(1L, DocumentType.CV))
                    .thenReturn(Optional.of(previousDoc));
            when(agreementMapper.toResponse(agreement)).thenReturn(TestFixtures.dummyAgreementResponse());
            doThrow(new ExternalServiceException("R2 unreachable", null))
                    .when(storageService).delete("agreements/1/cv/old.pdf");

            assertThatCode(() -> service.uploadDocument(1L, DocumentType.CV, file, currentUser))
                    .doesNotThrowAnyException();

            verify(agreementDocumentRepository).upsert(
                    eq(1L), eq("CV"), anyString(), anyString(), anyString(), anyLong(), eq(30L));
        }

        @Test
        void studentCannotUploadCompanyDocument() {
            var agreement   = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
            agreement.setStatus(AgreementStatus.DRAFT);
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);
            var file        = new org.springframework.mock.web.MockMultipartFile(
                    "file", "nit.pdf", "application/pdf", "x".getBytes());

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.uploadDocument(1L, DocumentType.NIT, file, currentUser))
                    .isInstanceOf(AccessDeniedException.class);
            verify(storageService, never()).upload(anyString(), anyString(), any());
        }

        @Test
        void tutorCannotUploadWhenNotAssignedToAgreement() {
            var agreement   = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
            agreement.setStatus(AgreementStatus.DRAFT);
            var otherTutor  = TestFixtures.jwtUser(99L, "COMPANY_TUTOR", 1L);
            var file        = new org.springframework.mock.web.MockMultipartFile(
                    "file", "nit.pdf", "application/pdf", "x".getBytes());

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.uploadDocument(1L, DocumentType.NIT, file, otherTutor))
                    .isInstanceOf(AccessDeniedException.class);
            verify(storageService, never()).upload(anyString(), anyString(), any());
        }

        @Test
        void studentContractRejectedWhenNotInPendingSignature() {
            var agreement   = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
            agreement.setStatus(AgreementStatus.DRAFT);
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);
            var file        = new org.springframework.mock.web.MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", "x".getBytes());

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.uploadDocument(1L, DocumentType.CONTRACT, file, currentUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PENDING_SIGNATURE");
            verify(storageService, never()).upload(anyString(), anyString(), any());
        }

        @Test
        void crossTenantUploadDenied() {
            var agreement   = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
            agreement.setStatus(AgreementStatus.DRAFT);
            var foreign     = TestFixtures.jwtUser(30L, "STUDENT", 2L);
            var file        = new org.springframework.mock.web.MockMultipartFile(
                    "file", "cv.pdf", "application/pdf", "x".getBytes());

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.uploadDocument(1L, DocumentType.CV, file, foreign))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void r2UploadFailureLeavesNoDatabaseRow() {
            var agreement   = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
            agreement.setStatus(AgreementStatus.DRAFT);
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);
            var file        = new org.springframework.mock.web.MockMultipartFile(
                    "file", "cv.pdf", "application/pdf", "bytes".getBytes());

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementDocumentRepository.findByAgreementIdAndDocumentType(1L, DocumentType.CV))
                    .thenReturn(Optional.empty());
            when(storageService.upload(anyString(), anyString(), any(byte[].class)))
                    .thenThrow(new ExternalServiceException("R2 unreachable", null));

            assertThatThrownBy(() -> service.uploadDocument(1L, DocumentType.CV, file, currentUser))
                    .isInstanceOf(ExternalServiceException.class);

            verify(agreementDocumentRepository, never()).upsert(
                    anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong());
            verify(storageService, never()).delete(anyString());
        }
    }

    // ── downloadDocument ──────────────────────────────────────────────────────

    @Nested
    class DownloadDocumentTests {

        @Test
        void returnsBytesWhenDocumentExists() {
            var agreement   = activeAgreement();
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);
            var doc = AgreementDocument.builder()
                    .id(5L).agreement(agreement).documentType(DocumentType.CV)
                    .fileKey("agreements/1/cv/uuid.pdf")
                    .uploadedBy(studentUser)
                    .build();

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementDocumentRepository.findByAgreementIdAndDocumentType(1L, DocumentType.CV))
                    .thenReturn(Optional.of(doc));
            when(storageService.download("agreements/1/cv/uuid.pdf"))
                    .thenReturn("pdf-bytes".getBytes());

            var result = service.downloadDocument(1L, DocumentType.CV, currentUser);

            assertThat(result).isEqualTo("pdf-bytes".getBytes());
        }

        @Test
        void throwsWhenDocumentNotUploaded() {
            var agreement   = activeAgreement();
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementDocumentRepository.findByAgreementIdAndDocumentType(1L, DocumentType.CV))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.downloadDocument(1L, DocumentType.CV, currentUser))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void crossTenantDownloadDenied() {
            var agreement = activeAgreement();
            var foreign   = TestFixtures.jwtUser(1L, "COORDINATOR", 2L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.downloadDocument(1L, DocumentType.CV, foreign))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void deniesStudentWhoIsNotOwner() {
            var agreement    = activeAgreement();
            var otherStudent = TestFixtures.jwtUser(99L, "STUDENT", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.downloadDocument(1L, DocumentType.CV, otherStudent))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(storageService);
        }

        @Test
        void deniesTutorNotAssignedToAgreement() {
            var agreement  = activeAgreement();
            var otherTutor = TestFixtures.jwtUser(99L, "COMPANY_TUTOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.downloadDocument(1L, DocumentType.CV, otherTutor))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ── listDocuments ─────────────────────────────────────────────────────────

    @Nested
    class ListDocumentsTests {

        @Test
        void ownerStudentGetsMetadataWithoutFileKey() {
            var agreement   = activeAgreement();
            var currentUser = TestFixtures.jwtUser(30L, "STUDENT", 1L);
            var doc = AgreementDocument.builder()
                    .id(7L).agreement(agreement).documentType(DocumentType.CV)
                    .fileKey("agreements/1/cv/uuid.pdf")
                    .originalName("cv.pdf").contentType("application/pdf")
                    .fileSizeBytes(1024L).uploadedBy(studentUser)
                    .build();

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementDocumentRepository.findAllByAgreementId(1L)).thenReturn(List.of(doc));

            var result = service.listDocuments(1L, currentUser);

            assertThat(result).hasSize(1);
            var item = result.getFirst();
            assertThat(item.documentType()).isEqualTo(DocumentType.CV);
            assertThat(item.originalName()).isEqualTo("cv.pdf");
            assertThat(item.uploadedById()).isEqualTo(30L);
        }

        @Test
        void coordinatorInSameTenantSeesList() {
            var agreement   = activeAgreement();
            var coordinator = TestFixtures.jwtUser(5L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementDocumentRepository.findAllByAgreementId(1L)).thenReturn(List.of());

            assertThatCode(() -> service.listDocuments(1L, coordinator))
                    .doesNotThrowAnyException();
        }

        @Test
        void unassignedTutorInSameTenantDenied() {
            var agreement  = activeAgreement();
            var otherTutor = TestFixtures.jwtUser(99L, "COMPANY_TUTOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.listDocuments(1L, otherTutor))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void crossTenantDenied() {
            var agreement = activeAgreement();
            var foreign   = TestFixtures.jwtUser(5L, "COORDINATOR", 2L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.listDocuments(1L, foreign))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Agreement activeAgreement() {
        return TestFixtures.activeAgreement(university, student, company, advisor, tutor);
    }

    private Agreement evaluationAgreement() {
        var a = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
        a.setStatus(AgreementStatus.EVALUATION);
        return a;
    }

    private Agreement finishedAgreement() {
        var a = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
        a.setStatus(AgreementStatus.FINISHED);
        a.setAdvisorGrade(new BigDecimal("4.0"));
        a.setCompanyGrade(new BigDecimal("3.0"));
        a.setFinalGrade(new BigDecimal("3.5"));
        return a;
    }
}
