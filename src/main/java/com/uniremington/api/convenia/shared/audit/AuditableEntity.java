package com.uniremington.api.convenia.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Abstract base entity that provides automatic auditing fields.
 *
 * <p>
 * All entities in the system must extend this class to inherit
 * {@code createdAt} and {@code updatedAt} fields, which are automatically
 * populated by Spring Data JPA's auditing infrastructure.
 * </p>
 *
 * <p>
 * Requires {@code @EnableJpaAuditing} to be declared in a
 * {@code @Configuration} class (see {@code JpaAuditingConfig}).
 * </p>
 *
 * @see org.springframework.data.jpa.domain.support.AuditingEntityListener
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class AuditableEntity {

    /**
     * Timestamp when the entity was first persisted. Set once, never updated.
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last modification. Updated automatically on every save.
     */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
