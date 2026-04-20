package com.uniremington.api.convenia.model.entity;

import com.uniremington.api.convenia.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable audit record of every status transition on a practice agreement.
 *
 * <p>
 * Every time an {@link Agreement} changes its {@link AgreementStatus}, one row
 * is inserted here capturing the before/after state, who triggered the change,
 * and any associated notes (e.g., the rejection reason). Records are never
 * updated or deleted — this table is append-only.
 * </p>
 *
 * <p>
 * This provides full legal traceability required by Resolución 002-2024:
 * coordinators and auditors can reconstruct the complete lifecycle of any
 * agreement at any point in time.
 * </p>
 */
@Entity
@Table(name = "agreement_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementStatusHistory extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The agreement whose status changed. Never null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id", nullable = false)
    private Agreement agreement;

    /**
     * The status before the transition. Null only for the initial DRAFT creation.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private AgreementStatus fromStatus;

    /**
     * The status after the transition. Never null.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private AgreementStatus toStatus;

    /**
     * The platform user who triggered the transition.
     * Null only for system-triggered transitions (e.g., Documenso webhook activations).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    /**
     * Optional contextual notes for this transition.
     * Always populated for {@link AgreementStatus#REJECTED} transitions
     * (contains the rejection reason). May be blank for other transitions.
     */
    @Column(columnDefinition = "TEXT")
    private String notes;
}
