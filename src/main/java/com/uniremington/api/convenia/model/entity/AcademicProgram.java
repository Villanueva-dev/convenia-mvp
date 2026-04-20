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
 * Represents an academic program (career) offered by a university.
 *
 * <p>
 * Each university manages its own catalog of programs. This entity
 * is referenced by {@code Student} to indicate which program the
 * student is enrolled in.
 * </p>
 *
 * <p>
 * Examples: "Systems Engineering", "Business Administration".
 * </p>
 */
@Entity
@Table(name = "academic_programs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicProgram extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The university that owns this academic program.
     * Uses LAZY fetch to avoid loading the full university on every query.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    /**
     * Full name of the academic program.
     * Example: "Systems Engineering".
     */
    @Column(nullable = false)
    private String name;

    /**
     * Faculty or department that the program belongs to.
     * Example: "Faculty of Engineering".
     */
    @Column(nullable = false)
    private String faculty;

    /**
     * Total number of semesters the program lasts.
     * Example: 10 for a typical engineering degree.
     */
    @Column(nullable = false)
    private Integer durationSemesters;

    /**
     * Whether this is a professional or technological degree program.
     * Determines the minimum credit percentage for practice eligibility:
     * PROFESSIONAL = 80%, TECHNOLOGICAL = 70% (Resolución 002-2024, Art. §1).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProgramType programType = ProgramType.PROFESSIONAL;

    /**
     * Total number of credits in the program curriculum.
     * Used with {@code Student.approvedCredits} to compute the eligibility percentage.
     */
    @Column(nullable = false)
    private Integer totalCredits;

    /**
     * When {@code false}, the program is no longer offered and should
     * not appear in student registration dropdowns.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
