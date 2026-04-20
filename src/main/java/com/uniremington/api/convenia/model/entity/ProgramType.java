package com.uniremington.api.convenia.model.entity;

/**
 * Classifies an academic program as professional or technological.
 *
 * <p>
 * This value determines the minimum credit percentage a student must
 * have approved before being eligible to start a professional practice,
 * per Resolución 002-2024:
 * </p>
 * <ul>
 *   <li>{@link #PROFESSIONAL} — 80% of total program credits required.</li>
 *   <li>{@link #TECHNOLOGICAL} — 70% of total program credits required.</li>
 * </ul>
 */
public enum ProgramType {

    /**
     * Professional degree program (Programa Profesional).
     * Requires 80% of total credits approved before the student can
     * submit a practice agreement.
     */
    PROFESSIONAL,

    /**
     * Technological degree program (Programa Tecnológico).
     * Requires 70% of total credits approved before the student can
     * submit a practice agreement.
     */
    TECHNOLOGICAL
}
