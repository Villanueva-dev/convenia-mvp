package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.model.dto.CreateVisitRequest;
import com.uniremington.api.convenia.model.dto.VisitResponse;
import com.uniremington.api.convenia.model.entity.*;
import com.uniremington.api.convenia.model.vo.JwtUser;
import com.uniremington.api.convenia.repository.AgreementRepository;
import com.uniremington.api.convenia.repository.PracticeVisitRepository;
import com.uniremington.api.convenia.repository.UserRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisitServiceImplTest {

    @Mock AgreementRepository     agreementRepository;
    @Mock PracticeVisitRepository visitRepository;
    @Mock UserRepository          userRepository;

    @InjectMocks VisitServiceImpl service;

    University university;
    User       advisor;
    User       tutor;
    User       studentUser;
    Student    student;
    Company    company;
    Agreement  agreement;

    @BeforeEach
    void setUp() {
        university  = TestFixtures.university(1L);
        advisor     = TestFixtures.user(10L, UserRole.ACADEMIC_ADVISOR, university);
        tutor       = TestFixtures.user(20L, UserRole.COMPANY_TUTOR, university);
        studentUser = TestFixtures.user(30L, UserRole.STUDENT, university);
        student     = TestFixtures.student(1L, studentUser, university);
        company     = TestFixtures.company(1L, university);
        agreement   = TestFixtures.activeAgreement(university, student, company, advisor, tutor);
    }

    // ── registerVisit ─────────────────────────────────────────────────────────

    @Nested
    class RegisterVisit {

        @Test
        void advisorRegistersVisitSuccessfully() {
            var request     = new CreateVisitRequest(LocalDate.now(), VisitType.IN_PERSON, "Todo bien con el practicante");
            var currentUser = TestFixtures.jwtUser(10L, "ACADEMIC_ADVISOR", 1L);
            var savedVisit  = buildVisit(agreement, advisor, request);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(userRepository.getReferenceById(10L)).thenReturn(advisor);
            when(visitRepository.save(any())).thenReturn(savedVisit);

            var result = service.registerVisit(1L, request, currentUser);

            assertThat(result).isNotNull();
            assertThat(result.visitType()).isEqualTo("IN_PERSON");
            verify(visitRepository).save(any(PracticeVisit.class));
        }

        @Test
        void throwsWhenAgreementNotActive() {
            agreement.setStatus(AgreementStatus.DRAFT);
            var request     = new CreateVisitRequest(LocalDate.now(), VisitType.VIRTUAL, "Observaciones");
            var currentUser = TestFixtures.jwtUser(10L, "ACADEMIC_ADVISOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.registerVisit(1L, request, currentUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        void throwsWhenAdvisorNotAssignedToAgreement() {
            var request   = new CreateVisitRequest(LocalDate.now(), VisitType.VIRTUAL, "Observaciones");
            var otherUser = TestFixtures.jwtUser(99L, "ACADEMIC_ADVISOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.registerVisit(1L, request, otherUser))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void throwsWhenAgreementNotFound() {
            when(agreementRepository.findById(999L)).thenReturn(Optional.empty());
            var currentUser = TestFixtures.jwtUser(10L, "ACADEMIC_ADVISOR", 1L);
            var request     = new CreateVisitRequest(LocalDate.now(), VisitType.VIRTUAL, "Observaciones");

            assertThatThrownBy(() -> service.registerVisit(999L, request, currentUser))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── listVisits ────────────────────────────────────────────────────────────

    @Nested
    class ListVisits {

        @Test
        void returnsVisitsForAssignedAdvisor() {
            var currentUser = TestFixtures.jwtUser(10L, "ACADEMIC_ADVISOR", 1L);
            var visit       = buildVisit(agreement, advisor,
                    new CreateVisitRequest(LocalDate.now(), VisitType.IN_PERSON, "Obs"));

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(visitRepository.findByAgreementIdOrderByVisitDateAsc(1L)).thenReturn(List.of(visit));

            var result = service.listVisits(1L, currentUser);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).agreementId()).isEqualTo(1L);
        }

        @Test
        void returnsEmptyListWhenNoVisits() {
            var currentUser = TestFixtures.jwtUser(1L, "COORDINATOR", 1L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(visitRepository.findByAgreementIdOrderByVisitDateAsc(1L)).thenReturn(List.of());

            assertThat(service.listVisits(1L, currentUser)).isEmpty();
        }

        @Test
        void throwsWhenAccessingFromDifferentTenant() {
            var differentTenant = TestFixtures.jwtUser(1L, "COORDINATOR", 2L);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));

            assertThatThrownBy(() -> service.listVisits(1L, differentTenant))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void adminBypassesTenantCheck() {
            var admin = TestFixtures.jwtUser(1L, "ADMIN", null);

            when(agreementRepository.findById(1L)).thenReturn(Optional.of(agreement));
            when(visitRepository.findByAgreementIdOrderByVisitDateAsc(1L)).thenReturn(List.of());

            assertThatCode(() -> service.listVisits(1L, admin)).doesNotThrowAnyException();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PracticeVisit buildVisit(Agreement agreement, User advisor, CreateVisitRequest req) {
        var visit = PracticeVisit.builder()
                .id(1L)
                .agreement(agreement)
                .advisor(advisor)
                .visitDate(req.visitDate())
                .visitType(req.visitType())
                .observations(req.observations())
                .build();
        return visit;
    }
}
