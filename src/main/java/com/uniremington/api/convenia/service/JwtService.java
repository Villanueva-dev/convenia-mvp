package com.uniremington.api.convenia.service;

import java.util.Map;

/**
 * Contract for JWT (JSON Web Token) operations.
 *
 * <p>
 * Separating the interface from the implementation follows the
 * Dependency Inversion Principle (SOLID): callers depend on this
 * abstraction, not on the concrete {@code JwtServiceImpl}.
 * </p>
 */
public interface JwtService {

    /**
     * Generates a signed JWT with custom claims and a subject.
     *
     * @param claims  key-value pairs to embed in the token payload (e.g., user-id, role)
     * @param subject the token subject, typically the user's email
     * @return a compact signed JWT string
     */
    String generateToken(Map<String, Object> claims, String subject);

    /**
     * Validates a JWT: checks signature and expiration.
     *
     * @param token the JWT string to validate
     * @return {@code true} if the token is valid and not expired
     */
    boolean validateToken(String token);

    /**
     * Extracts the subject (email) from a token.
     *
     * @param token a valid JWT string
     * @return the subject claim value
     */
    String getSubject(String token);

    /**
     * Extracts a single claim value from a token.
     *
     * @param token    a valid JWT string
     * @param claimKey the key of the claim to extract
     * @param clazz    the expected type of the claim value
     * @param <T>      the return type
     * @return the claim value cast to {@code T}, or {@code null} if absent
     */
    <T> T getClaim(String token, String claimKey, Class<T> clazz);
}
