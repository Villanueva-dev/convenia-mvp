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
 * Represents an authenticated user in the platform.
 *
 * <p>
 * This entity stores login credentials and is intentionally decoupled
 * from {@code Student}. This separation allows different actor types
 * (students, university coordinators, platform admins) to share the same
 * authentication infrastructure.
 * </p>
 *
 * <p>
 * An {@code ADMIN} user has a {@code null} university because
 * they operate at the platform level, not within any single tenant.
 * </p>
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email address used as the login username. Must be globally unique
     * across all universities to prevent duplicate accounts.
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * BCrypt-hashed password. Never stored or transmitted in plain text.
     */
    @Column(nullable = false)
    private String password;

    /**
     * Authorization role determining what actions this user can perform.
     * Stored as a string (not ordinal) to avoid reordering issues.
     *
     * @see UserRole
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /**
     * The university this user belongs to. Provides tenant-level data isolation.
     *
     * <p>
     * This field is {@code null} only for {@link UserRole#ADMIN} users,
     * who operate across all universities.
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    private University university;

    /**
     * Soft-disable flag. When {@code false}, the user cannot authenticate
     * but their data is preserved for audit trails and historical records.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
