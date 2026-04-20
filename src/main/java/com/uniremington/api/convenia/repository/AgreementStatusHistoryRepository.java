package com.uniremington.api.convenia.repository;

import com.uniremington.api.convenia.model.entity.AgreementStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Data access layer for {@link AgreementStatusHistory} records.
 *
 * <p>This repository is append-only: records are never updated or deleted.</p>
 */
public interface AgreementStatusHistoryRepository extends JpaRepository<AgreementStatusHistory, Long> {

    /**
     * Returns the complete status transition history for a given agreement,
     * ordered chronologically (oldest first).
     *
     * @param agreementId  The unique identifier of the agreement.
     * @return             Ordered list of status change records.
     */
    List<AgreementStatusHistory> findByAgreementIdOrderByCreatedAtAsc(Long agreementId);
}
