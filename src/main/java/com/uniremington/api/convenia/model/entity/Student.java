package com.uniremington.api.convenia.model.entity;

import com.uniremington.api.convenia.shared.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a student's academic identity in the platform.
 *
 * <p>
 * A student self-registers via the public registration endpoint.
 * Their email domain is validated against the university's
 * {@code emailDomain} to ensure they belong to that institution.
 * </p>
 *
 * <p>
 * This entity is intentionally separated from {@link User} following
 * the Single Responsibility Principle: {@code User} handles authentication,
 * {@code Student} holds academic and personal data.
 * </p>
 *
 * <p>
 * The {@code documentNumber} (national ID) is unique per university,
 * allowing the same person to potentially exist in different institutions
 * without conflicts.
 * </p>
 */
@Entity
@Table(name = "students", uniqueConstraints = @UniqueConstraint(name = "uk_student_document_university", columnNames = {
                "document_number", "university_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student extends AuditableEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        /**
         * One-to-one link to the authentication account.
         * A student always has exactly one login credential.
         */
        @OneToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false, unique = true)
        private User user;

        /**
         * The university this student belongs to. Used for tenant data isolation.
         */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "university_id", nullable = false)
        private University university;

        /**
         * The academic program the student is enrolled in.
         * Example: "Systems Engineering".
         */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "academic_program_id", nullable = false)
        private AcademicProgram academicProgram;

        /**
         * Full legal name as it appears on the student's ID document.
         * This is the name that will appear in the generated agreement PDF.
         */
        @Column(nullable = false)
        private String fullName;

        /**
         * National identification number (e.g., cédula de ciudadanía).
         * Unique within each university (composite unique constraint).
         */
        @Column(name = "document_number", nullable = false)
        private String documentNumber;

        @Column(nullable = false)
        private String phoneNumber;

        private String address;

        /**
         * Current academic semester (1-based).
         * Used as a reference indicator of the student's academic progress.
         */
        @Column(nullable = false)
        private Integer currentSemester;

        /**
         * Number of credits the student has approved to date.
         * Compared against {@code AcademicProgram.totalCredits} to determine
         * eligibility: must be ≥ 80% for PROFESSIONAL programs or ≥ 70% for
         * TECHNOLOGICAL programs (Resolución 002-2024, Art. §1).
         */
        @Column(nullable = false)
        @Builder.Default
        private Integer approvedCredits = 0;

        /**
         * Number of mandatory practice-preparation seminars the student has completed.
         * Must be at least 2 before an agreement can be submitted for review
         * (Resolución 002-2024, Art. §1).
         * Valid range: 0–2.
         */
        @Column(nullable = false)
        @Builder.Default
        private Integer seminarsCompleted = 0;
}
