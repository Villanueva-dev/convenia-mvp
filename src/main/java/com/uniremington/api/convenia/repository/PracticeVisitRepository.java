package com.uniremington.api.convenia.repository;

import com.uniremington.api.convenia.model.entity.PracticeVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Data access layer for {@link PracticeVisit} records.
 */
public interface PracticeVisitRepository extends JpaRepository<PracticeVisit, Long> {

    /**
     * Returns all visits registered for a given agreement, ordered by visit date ascending.
     *
     * @param agreementId  The unique identifier of the agreement.
     * @return             Ordered list of visits for the agreement.
     */
    List<PracticeVisit> findByAgreementIdOrderByVisitDateAsc(Long agreementId);

    /**
     * Counts the number of visits registered for a given agreement.
     * Used to enforce the minimum three (3) visits rule before closing a practice.
     *
     * @param agreementId  The unique identifier of the agreement.
     * @return             Total number of visits recorded.
     */
    long countByAgreementId(Long agreementId);
}
