package com.uniremington.api.convenia.model.entity;

/**
 * Defines the lifecycle states of a professional practice agreement.
 *
 * <p>
 * The valid state transitions follow the two-stage approval process
 * defined in Resolución 002-2024:
 * </p>
 * 
 * <pre>
 *   DRAFT ──► ADMIN_REVIEW ──► COORDINATION_REVIEW ──► PENDING_SIGNATURE ──► ACTIVE ──► EVALUATION ──► FINISHED
 *               │                       │
 *           (back to DRAFT)          REJECTED
 * </pre>
 *
 * <p>
 * Stored as {@code VARCHAR} in the database via
 * {@code @Enumerated(EnumType.STRING)} to prevent ordinal-reordering bugs.
 * Never use {@code EnumType.ORDINAL}: adding a new status between existing
 * ones would silently corrupt historical data.
 * </p>
 */
public enum AgreementStatus {

    /**
     * Agreement created by the student but not yet submitted for review.
     * Can still be edited and documents can be attached freely.
     */
    DRAFT,

    /**
     * Submitted by the student. The administrative secretary ({@code COORDINATOR})
     * validates legal company documents (RUT, Cámara de Comercio, NIT).
     * Cannot be edited while under review.
     */
    ADMIN_REVIEW,

    /**
     * Passed administrative validation. The academic coordination
     * ({@code COORDINATOR}) audits and endorses the academic practice.
     * Verifies that the student meets the credit and eligibility requirements.
     */
    COORDINATION_REVIEW,

    /**
     * Approved by coordination. The PDF has been generated via Thymeleaf
     * and sent to Documenso for digital signatures from all parties.
     */
    PENDING_SIGNATURE,

    /**
     * Documenso webhook confirmed all signatures. The internship is
     * officially underway. The follow-up module (minimum 3 visits) is enabled.
     */
    ACTIVE,

    /**
     * The internship period has ended. Both the academic advisor and the company
     * tutor must now submit their evaluations (50% each). Transitions automatically
     * to {@link #FINISHED} once both grades are recorded.
     */
    EVALUATION,

    /**
     * Both evaluations have been submitted and the final grade has been computed.
     * Terminal state — the agreement is closed and a completion certificate can
     * be generated.
     */
    FINISHED,

    /**
     * The agreement was rejected at the COORDINATION_REVIEW stage (terminal).
     * For ADMIN_REVIEW rejections the agreement is returned to {@link #DRAFT}
     * so the student can apply corrections.
     * A {@code rejectionReason} must be provided on the {@link Agreement} entity.
     */
    REJECTED
}
