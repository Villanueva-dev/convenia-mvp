package com.uniremington.api.convenia.model.entity;

import com.uniremington.api.convenia.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a university tenant in the multi-tenant SaaS platform.
 *
 * <p>
 * Every data-bearing entity in the system (Students, Agreements, Users)
 * belongs to exactly one University. This is the root of data isolation:
 * a coordinator from University A can never access data from University B.
 * </p>
 *
 * <p>
 * Only a {@code ADMIN} user can create or deactivate university records.
 * </p>
 */
@Entity
@Table(name = "universities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class University extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Official full name of the university.
     * Example: "Corporación Universitaria Remington".
     */
    @Column(nullable = false)
    private String name;

    /**
     * Short abbreviation used in UI and PDFs.
     * Example: "Uniremington".
     */
    @Column(nullable = false)
    private String shortName;

    /**
     * Email domain used to validate student self-registration.
     * Example: "uniremington.edu.co".
     *
     * <p>
     * When a student registers, the system verifies that their email
     * ends with this domain to prevent unauthorized sign-ups.
     * </p>
     */
    @Column(nullable = false, unique = true)
    private String emailDomain;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    /**
     * URL pointing to the university's logo in cloud storage.
     * Used to brand generated PDF agreements with the institution's identity.
     */
    private String logoUrl;

    /**
     * Soft-delete flag. When {@code false}, the university tenant is disabled
     * and all its users lose access without deleting any data.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
