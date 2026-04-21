package com.uniremington.api.convenia.model.entity;

/**
 * Defines the authorization roles available in the platform.
 *
 * <p>
 * The five roles map directly to the actors defined in the university
 * practice regulations (Resolución 002-2024) and the system's entity
 * relationship model. Each role is bound to a specific university tenant,
 * except {@link #ADMIN} which operates at the platform level.
 * </p>
 *
 * <p>
 * Stored as a {@code VARCHAR} in the database via
 * {@code @Enumerated(EnumType.STRING)} to avoid ordinal-position bugs
 * when new roles are added.
 * </p>
 */
public enum UserRole {

    /**
     * Platform-level administrator (IT team / SaaS owner).
     * Can create and manage university tenants.
     * Not bound to any specific university ({@code universityId} is null).
     */
    ADMIN,

    /**
     * Administrative secretary of the faculty.
     * Supports the coordinator with administrative tasks related to
     * practice agreements (document reception, scheduling, notifications).
     */
    SECRETARY,

    /**
     * Practice coordinator ("Docente Coordinador de Prácticas").
     * Belongs to the university. Plans, coordinates and approves agreements.
     * Administers the information system and handles the full agreement lifecycle.
     */
    COORDINATOR,

    /**
     * Academic advisor ("Docente Asesor") assigned by the faculty.
     * Monitors the student during the internship, conducts the mandatory
     * minimum three (3) site visits, submits evaluation reports,
     * and assigns 50% of the final grade.
     */
    ACADEMIC_ADVISOR,

    /**
     * Company tutor ("Tutor / Co-formador") designated by the host company.
     * Guides the intern on a day-to-day basis and assigns 50% of the final grade.
     * Distinct from the company's legal representative who signs the agreement PDF.
     *
     * @see com.uniremington.api.convenia.model.entity.Company#getRepresentativeName()
     */
    COMPANY_TUTOR,

    /**
     * Student ("Practicante / Pasante").
     * Self-registers on the platform, creates draft agreements,
     * and uploads the required supporting documents.
     */
    STUDENT
}
