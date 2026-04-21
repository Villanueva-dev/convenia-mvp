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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Central entity of the platform: a professional practice agreement
 * ("convenio de práctica") between a student, a university, and a host company.
 *
 * <p>
 * Three parties are involved in every agreement:
 * </p>
 * <ol>
 * <li><strong>Student</strong> — the intern who performs the practice.</li>
 * <li><strong>University</strong>, represented by an {@code academicAdvisor}
 * (a {@code User} with role {@code ACADEMIC_ADVISOR}) who monitors the
 * student, conducts site visits, and grades 50% of the final score.</li>
 * <li><strong>Company</strong>, represented by a {@code companyRep}
 * (a {@code User} with role {@code COMPANY_TUTOR}) who guides the intern
 * daily and grades the other 50% of the final score.</li>
 * </ol>
 *
 * <p>
 * The agreement follows a two-stage approval state machine defined by
 * {@link AgreementStatus}. State transitions are enforced in the service layer,
 * not at the entity level, following the Single Responsibility Principle.
 * </p>
 *
 * <p>
 * The {@code university} field is kept here (even though derivable from
 * {@code student.university}) as the tenant discriminator for row-level
 * security queries, avoiding expensive JOINs on every filtered read.
 * </p>
 */
@Entity
@Table(name = "agreements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agreement extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Three parties ─────────────────────────────────────────────────────────

    /**
     * The university this agreement belongs to.
     * Acts as the tenant discriminator for multi-tenant data isolation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    private University university;

    /**
     * The student who will perform the professional practice.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /**
     * The host company where the student will carry out the internship.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // ── Two key protagonists assigned at agreement creation ───────────────────

    /**
     * The academic advisor ("Docente Asesor") assigned by the university faculty.
     * Must hold the {@code ACADEMIC_ADVISOR} role.
     *
     * <p>
     * Responsibilities: conduct a minimum of 3 site visits, submit
     * evaluation reports, and assign 50% of the final grade (scale 0.0–5.0).
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_advisor_id", nullable = false)
    private User academicAdvisor;

    /**
     * The company representative ("Tutor / Co-formador") at the host company.
     * Must hold the {@code COMPANY_TUTOR} role.
     *
     * <p>
     * Responsibilities: guide the intern on a day-to-day basis and
     * assign the other 50% of the final grade (scale 0.0–5.0).
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_rep_id", nullable = false)
    private User companyRep;

    // ── Agreement configuration ───────────────────────────────────────────────

    /**
     * The modality of the professional practice (PROFESSIONAL, SOCIAL, RESEARCH, INTERNATIONAL).
     * Cannot be changed once the agreement leaves {@link AgreementStatus#DRAFT}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PracticeModality practiceModality;

    /**
     * The academic component of the practice (ACADEMIC, SOCIAL, MANAGEMENT).
     * Describes the nature and purpose of the student's activities.
     * Cannot be changed once the agreement leaves {@link AgreementStatus#DRAFT}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PracticeComponent practiceComponent;

    /**
     * The legal contract type binding the student to the company.
     *
     * <p>
     * <strong>Business rule:</strong> if {@code APPRENTICESHIP}, the service
     * layer must enforce a fixed 6-month duration between {@code startDate}
     * and {@code endDate}.
     * </p>
     *
     * @see ContractType
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractType contractType;

    /**
     * First day of the professional practice period.
     */
    @Column(nullable = false)
    private LocalDate startDate;

    /**
     * Last day of the professional practice period.
     * Minimum 4 months from {@code startDate}, maximum 12 months.
     * Fixed at exactly 6 months when {@code contractType} is
     * {@code APPRENTICESHIP}.
     */
    @Column(nullable = false)
    private LocalDate endDate;

    /**
     * Hours per week the intern is expected to work.
     * Valid range per regulation: 20 (minimum) to 48 (maximum).
     */
    @Column(nullable = false)
    private Integer weeklyHours;

    /**
     * Monthly compensation for the intern. Uses {@link BigDecimal} to avoid
     * floating-point rounding errors. Can be zero for unpaid internships.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyStipend;

    // ── Workflow state ────────────────────────────────────────────────────────

    /**
     * Current lifecycle status of this agreement.
     * Defaults to {@link AgreementStatus#DRAFT} on creation.
     * Stored as VARCHAR — never use {@code EnumType.ORDINAL}.
     *
     * @see AgreementStatus
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AgreementStatus status = AgreementStatus.DRAFT;

    /**
     * Human-readable reason when the agreement is rejected.
     * Must be populated whenever {@code status} is set to
     * {@link AgreementStatus#REJECTED}.
     */
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    // ── Document management (Cloudflare R2 / S3 file keys) ───────────────────

    /**
     * S3/R2 file key for the student's curriculum vitae (CV).
     * Required during the first month of the active practice.
     */
    private String cvFileKey;

    /**
     * S3/R2 file key for the scanned labor or apprenticeship contract.
     */
    private String contractFileKey;

    /**
     * S3/R2 file key for the student's national ID copy (cédula).
     */
    private String nationalIdFileKey;

    /**
     * S3/R2 file key for the student's health insurance certificate (EPS).
     */
    private String epsFileKey;

    /**
     * S3/R2 file key for the occupational risk insurance certificate (ARL).
     */
    private String arlFileKey;

    /**
     * S3/R2 file key for the approved internship work plan.
     */
    private String workPlanFileKey;

    // ── Documenso integration ─────────────────────────────────────────────────

    /**
     * Document identifier returned by the Documenso API after the PDF
     * is submitted for digital signatures.
     */
    private String documensoDocumentId;

    /**
     * Full URL pointing to the generated agreement PDF in cloud storage
     * (Cloudflare R2). Populated after the PDF is generated and uploaded.
     */
    private String pdfCloudUrl;

    // ── Evaluation — grading (populated during ACTIVE stage) ─────────────────

    /**
     * Grade assigned by the academic advisor (0.0 to 5.0).
     * Represents 50% of the final grade. Null until the advisor submits it.
     */
    @Column(precision = 3, scale = 1)
    private BigDecimal advisorGrade;

    /**
     * Grade assigned by the company representative (0.0 to 5.0).
     * Represents the other 50% of the final grade. Null until submitted.
     */
    @Column(precision = 3, scale = 1)
    private BigDecimal companyGrade;

    /**
     * Final computed grade: {@code (advisorGrade + companyGrade) / 2}.
     * Calculated automatically by the service layer when both grades are provided.
     * Null until both parties have submitted their evaluation.
     */
    @Column(precision = 3, scale = 1)
    private BigDecimal finalGrade;
}
