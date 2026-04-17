package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.model.dto.AuthResponse;
import com.uniremington.api.convenia.model.dto.LoginRequest;
import com.uniremington.api.convenia.repository.UserRepository;
import com.uniremington.api.convenia.service.AuthService;
import com.uniremington.api.convenia.service.JwtService;
import com.uniremington.api.convenia.shared.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the login flow: credential verification → JWT generation.
 *
 * <p>Step-by-step:</p>
 * <ol>
 *   <li>Delegate credential verification to Spring's {@link AuthenticationManager}.
 *       It calls {@code UserDetailsServiceImpl} internally, loads the user,
 *       and compares the BCrypt hash. If invalid → throws {@code BadCredentialsException}.</li>
 *   <li>Load the full {@code User} entity from the database to access role and universityId.</li>
 *   <li>Build a claims map: {@code user-id}, {@code role}, {@code university-id}.</li>
 *   <li>Generate a signed JWT via {@link JwtService}.</li>
 *   <li>Return an {@link AuthResponse} with the token and user metadata.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public AuthResponse login(LoginRequest request) {
        // ── Step 1: Verify credentials via Spring Security ────────────────────
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException | DisabledException ex) {
            log.warn("Failed login attempt for email: {}", request.email());
            throw new InvalidCredentialsException();
        }

        // ── Step 2: Load the user entity ──────────────────────────────────────
        // At this point credentials are valid, so the user definitely exists.
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        // ── Step 3: Build JWT claims ──────────────────────────────────────────
        Map<String, Object> claims = new HashMap<>();
        claims.put("user-id", user.getId());
        claims.put("role", user.getRole().name());
        // university-id is null for ADMIN — the frontend handles null gracefully
        claims.put("university-id",
                user.getUniversity() != null ? user.getUniversity().getId() : null);

        // ── Step 4: Generate the signed JWT ──────────────────────────────────
        String token = jwtService.generateToken(claims, user.getEmail());
        log.info("JWT issued for user: {} with role: {}", user.getEmail(), user.getRole());

        // ── Step 5: Return response ───────────────────────────────────────────
        return new AuthResponse(
                token,
                "Bearer",
                user.getEmail(),
                user.getRole().name(),
                user.getUniversity() != null ? user.getUniversity().getId() : null
        );
    }
}
