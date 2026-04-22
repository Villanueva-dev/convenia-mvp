package com.uniremington.api.convenia.config;

import com.uniremington.api.convenia.model.vo.JwtUser;
import com.uniremington.api.convenia.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Intercepts every HTTP request and validates the JWT from the Authorization header.
 *
 * <p>
 * Extends {@link OncePerRequestFilter} to guarantee exactly one execution per request,
 * even in redirect chains.
 * </p>
 *
 * <p>
 * Flow:
 * </p>
 * <ol>
 *   <li>Read the {@code Authorization: Bearer <token>} header.</li>
 *   <li>If missing or malformed → pass the request through without authentication.</li>
 *   <li>Validate the token via {@link JwtService#validateToken(String)}.</li>
 *   <li>If valid → extract claims (userId, role, universityId) and create a
 *       {@link JwtUser} principal stored in the {@link SecurityContextHolder}.</li>
 *   <li>Spring Security uses that context to enforce {@code @PreAuthorize} rules.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // ── 1. No header or wrong format → skip auth, continue filter chain ──
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix

        // ── 2. Invalid or expired token → reject with 401 ────────────────────
        if (!jwtService.validateToken(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        // ── 3. Valid token → extract claims and populate SecurityContext ──────
        try {
            Authentication auth = buildAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Failed to build authentication from JWT: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token claims are invalid");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts claims from the token and builds an {@link Authentication} object
     * with a {@link JwtUser} as the principal.
     *
     * @param token a validated JWT string
     * @return a fully populated {@link UsernamePasswordAuthenticationToken}
     */
    private Authentication buildAuthentication(String token) {
        String email = jwtService.getSubject(token);
        Long userId = jwtService.getClaim(token, "user-id", Long.class);
        String role = jwtService.getClaim(token, "role", String.class);
        Long universityId = jwtService.getClaim(token, "university-id", Long.class);

        // Spring Security authority: "ROLE_COORDINATOR", "ROLE_ADMIN", etc.
        var authority = new SimpleGrantedAuthority("ROLE_" + role);
        var authorities = List.of(authority);

        var principal = new JwtUser(email, userId, role, universityId, authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }
}
