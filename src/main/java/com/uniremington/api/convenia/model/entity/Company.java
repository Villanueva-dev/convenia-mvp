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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a company that hosts professional practice interns.
 *
 * <p>
 * A company is always registered under a specific university (tenant).
 * This means the same real-world company (e.g., "Bancolombia S.A.") can
 * appear multiple times in the system — once per university that works with it.
 * This design ensures strict data isolation between tenants.
 * </p>
 *
 * <p>
 * The {@code nit} (tax ID) is unique within a university, preventing
 * duplicate registrations of the same company for the same tenant.
 * </p>
 *
 * <p>
 * The {@code representativeName} and {@code representativeEmail} store the
 * legal representative who will appear in the agreement PDF and sign via
 * Documenso. This person is not a system user — they are an external contact.
 * </p>
 */
@Entity
@Table(name = "companies", uniqueConstraints = @UniqueConstraint(name = "uk_company_nit_university", columnNames = {
                "nit", "university_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company extends AuditableEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        /**
         * The university (tenant) this company is registered under.
         * A company cannot be shared across universities — each tenant
         * manages their own company directory.
         */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "university_id", nullable = false)
        private University university;

        /**
         * Official registered company name as it appears in legal documents.
         * This name will appear in the generated agreement PDF.
         * Example: "Bancolombia S.A."
         */
        @Column(name = "legal_name", nullable = false)
        private String legalName;

        /**
         * Colombian tax identification number (NIT).
         * Unique per university — prevents duplicate company entries per tenant.
         * Example: "890.929.067-6".
         */
        @Column(nullable = false)
        private String nit;

        /**
         * Full legal name of the company's representative who will sign the agreement.
         * This is an external contact — not a registered system user.
         * Example: "Juan Carlos Perez Gomez".
         */
        @Column(name = "representative_name", nullable = false)
        private String representativeName;

        /**
         * Email address of the legal representative.
         * Used by Documenso to send the agreement PDF for digital signature.
         */
        @Column(name = "representative_email", nullable = false)
        private String representativeEmail;
}
