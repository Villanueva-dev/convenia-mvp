package com.uniremington.api.convenia.model.entity;

/**
 * Academic component of the professional practice, as defined in Resolución 002-2024.
 * Describes the nature and purpose of the student's activities at the host organization.
 */
public enum PracticeComponent {

    /**
     * Academic component ("Componente Académico").
     * Strengthens skills, attitudes and values required for professional performance.
     */
    ACADEMIC,

    /**
     * Social component ("Componente Social").
     * The student's interaction must benefit society; collective wellbeing
     * takes priority over individual benefit.
     */
    SOCIAL,

    /**
     * Management component ("Componente de Gestión").
     * Develops the capacity to evaluate information and execute the practice project.
     */
    MANAGEMENT
}
