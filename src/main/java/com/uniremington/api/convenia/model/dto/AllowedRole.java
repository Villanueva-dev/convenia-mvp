package com.uniremington.api.convenia.model.dto;

/**
 * Roles that a COORDINATOR or ADMIN may create via the user management endpoint.
 *
 * <p>Note: {@code COORDINATOR} is listed here but the service layer restricts
 * its creation to {@code ADMIN} only — a coordinator cannot create another
 * coordinator (no horizontal privilege escalation).</p>
 */
public enum AllowedRole {
    ACADEMIC_ADVISOR,
    COMPANY_TUTOR,
    SECRETARY,
    COORDINATOR
}
