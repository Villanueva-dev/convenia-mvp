package com.uniremington.api.convenia.model.dto;

/**
 * Output DTO returned after a successful login.
 *
 * <p>
 * Contains the JWT token and basic user metadata so the frontend
 * does not need to decode the token to display user information.
 * </p>
 *
 * @param token        the signed JWT (Bearer token)
 * @param type         always {@code "Bearer"} — the token type per RFC 6750
 * @param email        the authenticated user's email
 * @param role         the user's role (e.g., ADMIN, COORDINATOR)
 * @param universityId the university the user belongs to;
 *                     {@code null} for ADMIN users (platform-level)
 */
public record AuthResponse(
        String token,
        String type,
        String email,
        String role,
        Long universityId
) {
}
