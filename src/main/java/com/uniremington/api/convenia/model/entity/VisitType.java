package com.uniremington.api.convenia.model.entity;

/**
 * Classifies how the academic advisor conducted a follow-up visit.
 *
 * <p>
 * Resolución 002-2024 requires a minimum of three (3) visits per
 * active agreement. Both modalities are accepted.
 * </p>
 */
public enum VisitType {

    /**
     * In-person visit at the company site ("Visita Presencial").
     */
    IN_PERSON,

    /**
     * Remote visit conducted via video call ("Visita Virtual").
     */
    VIRTUAL
}
