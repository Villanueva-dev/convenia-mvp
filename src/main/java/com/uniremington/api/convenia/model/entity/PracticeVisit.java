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

import java.time.LocalDate;

/**
 * Records a follow-up visit conducted by the academic advisor during
 * an active professional practice agreement.
 *
 * <p>
 * Resolución 002-2024 requires the academic advisor ({@code ACADEMIC_ADVISOR})
 * to register a minimum of <strong>three (3)</strong> visits or reports
 * per active agreement. The system must prevent closing the practice cycle
 * until this minimum is reached.
 * </p>
 *
 * <p>
 * Visits can be {@link VisitType#IN_PERSON} (at the company site) or
 * {@link VisitType#VIRTUAL} (video call). Both modalities are accepted
 * by the regulation.
 * </p>
 */
@Entity
@Table(name = "practice_visits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeVisit extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The active agreement this visit is associated with.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id", nullable = false)
    private Agreement agreement;

    /**
     * The academic advisor ({@code ACADEMIC_ADVISOR}) who conducted the visit.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advisor_id", nullable = false)
    private User advisor;

    /**
     * Calendar date on which the visit took place.
     */
    @Column(nullable = false)
    private LocalDate visitDate;

    /**
     * Whether the visit was conducted in person or remotely.
     *
     * @see VisitType
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VisitType visitType;

    /**
     * Summary of observations and findings recorded during the visit.
     * The advisor must describe the student's performance and any issues found.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String observations;
}
