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
import com.uniremington.api.convenia.util.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgreementServiceImplTest {

    @Mock AgreementRepository               agreementRepository;
    @Mock StudentRepository                 studentRepository;
    @Mock CompanyRepository                 companyRepository;
    @Mock UserRepository                    userRepository;
    @Mock AgreementStatusHistoryRepository  statusHistoryRepository;
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
        void transitionsActiveToEvaluation() {
            var agreement   = activeAgreement();
            var currentUser = TestFixtures.jwtUser(1L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(agreementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(statusHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.getReferenceById(1L)).thenReturn(advisor);
            when(agreementMapper.toResponse(any())).thenReturn(TestFixtures.dummyAgreementResponse());

            service.startEvaluation(1L, currentUser);

            assertThat(agreement.getStatus()).isEqualTo(AgreementStatus.EVALUATION);
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

    // ── helpers ───────────────────────────────────────────────────────────────

    private Agreement activeAgreement() {
        return TestFixtures.activeAgreement(university, student, company, advisor, tutor);
    }

    private Agreement evaluationAgreement() {
        var a = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
        a.setStatus(AgreementStatus.EVALUATION);
        return a;
    }
}
