package com.uniremington.api.convenia.model.entity;

/**
 * Defines the modality of a professional practice, as established in
 * Resolución 002-2024 of the Faculty of Engineering.
 *
 * <p>
 * The modality determines the nature and context of the internship.
 * It is selected by the student when creating the agreement and
 * cannot be changed once the agreement leaves {@link AgreementStatus#DRAFT}.
 * </p>
 *
 * <p>
 * Stored as {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)}.
 * </p>
 */
public enum PracticeModality {

    /**
     * Professional practice in a private or public company.
     * Most common modality. Requires a formal labor or apprenticeship contract.
     */
    PROFESSIONAL,

    /**
     * Social practice in a non-profit, community, or government organization.
     * Often unpaid or with a symbolic stipend.
     */
    SOCIAL,

    /**
     * Research practice embedded in a university or research group.
     * The student contributes to an active research project under faculty
     * supervision.
     */
    RESEARCH,

    /**
     * International practice in a foreign country or multinational organization.
     * Subject to additional legal and immigration requirements.
     */
    INTERNATIONAL
}
