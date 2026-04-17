package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT implementation using the HMAC-SHA-256 algorithm via the {@code jjwt} library.
 *
 * <p>
 * The secret key and expiration time are injected from {@code application.yml}:
 * </p>
 * <pre>
 * app:
 *   jwt:
 *     secret: ...   (min 32 chars for HMAC-256)
 *     expiration: 86400000  (24 hours in ms)
 * </pre>
 *
 * <p>
 * This class is package-private by convention: callers should always
 * depend on the {@link JwtService} interface, never on this concrete class.
 * </p>
 */
@Slf4j
@Service
class JwtServiceImpl implements JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration}")
    private long expirationMs;

    // ── Key helper ─────────────────────────────────────────────────────────────

    /**
     * Builds the {@link SecretKey} from the configured secret string.
     * Called on every operation to avoid storing the key as a field.
     */
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // ── Token generation ────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>
     * Builds a JWT with the given claims and subject, signed with HMAC-SHA-256.
     * The {@code iat} (issued-at) and {@code exp} (expiration) are set automatically.
     * </p>
     */
    @Override
    public String generateToken(Map<String, Object> claims, String subject) {
        Instant now = Instant.now();
        Instant expiration = now.plusMillis(expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey())
                .compact();
    }

    // ── Token validation ────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>
     * Parses the token and verifies the signature. Returns {@code false}
     * for any error (expired, malformed, invalid signature, etc.)
     * without throwing — callers receive a safe boolean.
     * </p>
     */
    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.warn("Malformed JWT: {}", e.getMessage());
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            log.warn("Unsupported JWT: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is null or empty: {}", e.getMessage());
        }
        return false;
    }

    // ── Claim extraction ────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T getClaim(String token, String claimKey, Class<T> clazz) {
        return parseClaims(token).get(claimKey, clazz);
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Parses the token and returns all claims.
     *
     * @param token a valid, non-expired JWT
     * @return the parsed {@link Claims} payload
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
