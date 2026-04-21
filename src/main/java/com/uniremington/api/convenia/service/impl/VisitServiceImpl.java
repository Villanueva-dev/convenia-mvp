package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.model.dto.CreateVisitRequest;
import com.uniremington.api.convenia.model.dto.VisitResponse;
import com.uniremington.api.convenia.model.entity.Agreement;
import com.uniremington.api.convenia.model.entity.AgreementStatus;
import com.uniremington.api.convenia.model.entity.PracticeVisit;
import com.uniremington.api.convenia.model.vo.JwtUser;
import com.uniremington.api.convenia.repository.AgreementRepository;
import com.uniremington.api.convenia.repository.PracticeVisitRepository;
import com.uniremington.api.convenia.repository.UserRepository;
import com.uniremington.api.convenia.service.VisitService;
import com.uniremington.api.convenia.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitServiceImpl implements VisitService {

    private final AgreementRepository    agreementRepository;
    private final PracticeVisitRepository visitRepository;
    private final UserRepository         userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<VisitResponse> listVisits(Long agreementId, JwtUser currentUser) {
        var agreement = loadAgreement(agreementId);
        assertTenantAccess(agreement, currentUser);
        return visitRepository.findByAgreementIdOrderByVisitDateAsc(agreementId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public VisitResponse registerVisit(Long agreementId, CreateVisitRequest request, JwtUser currentUser) {
        var agreement = loadAgreement(agreementId);
        assertTenantAccess(agreement, currentUser);

        if (agreement.getStatus() != AgreementStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Visits can only be registered for ACTIVE agreements. Current status: " + agreement.getStatus());
        }

        if (!agreement.getAcademicAdvisor().getId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Only the assigned academic advisor can register visits");
        }

        var visit = PracticeVisit.builder()
                .agreement(agreement)
                .advisor(userRepository.getReferenceById(currentUser.getUserId()))
                .visitDate(request.visitDate())
                .visitType(request.visitType())
                .observations(request.observations())
                .build();

        return toResponse(visitRepository.save(visit));
    }

    private Agreement loadAgreement(Long id) {
        return agreementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement", id));
    }

    private void assertTenantAccess(Agreement agreement, JwtUser user) {
        if (user.getUniversityId() != null &&
                !user.getUniversityId().equals(agreement.getUniversity().getId())) {
            throw new AccessDeniedException("Agreement does not belong to your university");
        }
    }

    private VisitResponse toResponse(PracticeVisit v) {
        return new VisitResponse(
                v.getId(),
                v.getAgreement().getId(),
                v.getAdvisor().getEmail(),
                v.getVisitDate(),
                v.getVisitType().name(),
                v.getObservations(),
                v.getCreatedAt()
        );
    }
}
