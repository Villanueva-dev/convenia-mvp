package com.uniremington.api.convenia.model.entity;

/**
 * Modality of the professional practice, as defined in Resolución 002-2024.
 * Selected by the student at agreement creation and cannot be changed once
 * the agreement leaves {@link AgreementStatus#DRAFT}.
 */
public enum PracticeModality {

    /** Professional practice in a private or public company. */
    PROFESSIONAL,

    /** Social practice in a non-profit, community, or government organization. */
    SOCIAL,

    /** Research practice embedded in a university or research group. */
    RESEARCH,

    /** International practice in a foreign country or multinational organization. */
    INTERNATIONAL
}
