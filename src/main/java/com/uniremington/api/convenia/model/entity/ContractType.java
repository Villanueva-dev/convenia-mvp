package com.uniremington.api.convenia.model.entity;

/**
 * Defines the legal contract type that binds the student to the host company,
 * as established in Resolución 002-2024.
 *
 * <p>
 * The contract type determines the legal obligations of each party,
 * the duration constraints, and the required documentation:
 * </p>
 * <ul>
 * <li>{@link #APPRENTICESHIP} duration is strictly fixed at 6 months
 * (SENA/SGVA).</li>
 * <li>All other types have a minimum of 4 months and a maximum of 12
 * months.</li>
 * </ul>
 *
 * <p>
 * Stored as {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)}.
 * </p>
 */
public enum ContractType {

    /**
     * Standard employment contract ("Contrato Laboral").
     * The student is hired as a regular employee with full labor rights.
     * Duration: 4 months minimum, 12 months maximum.
     */
    EMPLOYMENT,

    /**
     * Apprenticeship contract ("Contrato de Aprendizaje").
     * Managed under SENA or SGVA regulations.
     * Duration: strictly fixed at 6 months — enforced by the service layer.
     */
    APPRENTICESHIP,

    /**
     * Specific internship agreement ("Convenio Específico de Pasantía").
     * A formal agreement between the university and the company
     * without a direct employment relationship.
     * Duration: 4 months minimum, 12 months maximum.
     */
    INTERNSHIP_AGREEMENT,

    /**
     * Framework agreement ("Convenio Marco").
     * A general cooperation agreement between the university and the company
     * that covers one or more internship placements.
     * Duration: 4 months minimum, 12 months maximum.
     */
    FRAMEWORK_AGREEMENT
}
