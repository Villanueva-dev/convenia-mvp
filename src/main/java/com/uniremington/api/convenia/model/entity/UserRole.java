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
     * Company representative ("Tutor / Co-formador").
     * The person at the host company responsible for guiding the intern
     * on a day-to-day basis. Assigns the other 50% of the final grade.
     */
    COMPANY_REP,

    /**
     * Student ("Practicante / Pasante").
     * Self-registers on the platform, creates draft agreements,
     * and uploads the required supporting documents.
     */
    STUDENT
}
