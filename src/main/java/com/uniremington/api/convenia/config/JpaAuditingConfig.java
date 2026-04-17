package com.uniremington.api.convenia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA Auditing for automatic population of {@code @CreatedDate}
 * and {@code @LastModifiedDate} fields in all entities that extend
 * {@code AuditableEntity}.
 *
 * <p>Without this configuration, the audit annotations would be ignored
 * and the timestamp columns would remain {@code null}.</p>
 *
 * @see com.uniremington.api.convenia.shared.audit.AuditableEntity
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    // No additional beans needed — @EnableJpaAuditing activates the listener.
}
