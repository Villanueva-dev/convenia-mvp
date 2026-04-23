package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.model.dto.AuthResponse;
import com.uniremington.api.convenia.model.dto.LoginRequest;
import com.uniremington.api.convenia.model.dto.RegisterRequest;
import com.uniremington.api.convenia.model.entity.Student;
import com.uniremington.api.convenia.model.entity.University;
import com.uniremington.api.convenia.model.entity.User;
import com.uniremington.api.convenia.model.entity.UserRole;
import com.uniremington.api.convenia.repository.AcademicProgramRepository;
import com.uniremington.api.convenia.repository.StudentRepository;
import com.uniremington.api.convenia.repository.UniversityRepository;
import com.uniremington.api.convenia.repository.UserRepository;
import com.uniremington.api.convenia.service.AuthService;
import com.uniremington.api.convenia.service.JwtService;
import com.uniremington.api.convenia.shared.exception.DuplicateResourceException;
import com.uniremington.api.convenia.shared.exception.InvalidCredentialsException;
import com.uniremington.api.convenia.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final AuthenticationManager    authenticationManager;
    private final JwtService               jwtService;
    private final UserRepository           userRepository;
    private final UniversityRepository     universityRepository;
    private final AcademicProgramRepository academicProgramRepository;
    private final StudentRepository        studentRepository;
    private final PasswordEncoder          passwordEncoder;

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

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, UserRole role) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }

        var university = universityRepository.findById(request.universityId())
                .orElseThrow(() -> new ResourceNotFoundException("University", request.universityId()));

        var user = userRepository.save(User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(role)
                .university(university)
                .active(true)
                .build());

        if (role == UserRole.STUDENT) {
            validateStudentFields(request);
            var program = academicProgramRepository.findById(request.academicProgramId())
                    .orElseThrow(() -> new ResourceNotFoundException("AcademicProgram", request.academicProgramId()));

            studentRepository.save(Student.builder()
                    .user(user)
                    .university(university)
                    .academicProgram(program)
                    .fullName(request.fullName())
                    .documentNumber(request.documentNumber())
                    .phoneNumber(request.phoneNumber())
                    .currentSemester(request.currentSemester())
                    .build());
        }

        log.info("User registered: {} with role {}", user.getEmail(), role);
        return buildAuthResponse(user, university);
    }

    private void validateStudentFields(RegisterRequest request) {
        // fullName is validated at the DTO level (@NotBlank) for every role.
        if (request.documentNumber() == null || request.documentNumber().isBlank())
            throw new IllegalArgumentException("documentNumber is required for STUDENT registration");
        if (request.phoneNumber() == null || request.phoneNumber().isBlank())
            throw new IllegalArgumentException("phoneNumber is required for STUDENT registration");
        if (request.academicProgramId() == null)
            throw new IllegalArgumentException("academicProgramId is required for STUDENT registration");
        if (request.currentSemester() == null)
            throw new IllegalArgumentException("currentSemester is required for STUDENT registration");
    }

    private AuthResponse buildAuthResponse(User user, University university) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user-id", user.getId());
        claims.put("role", user.getRole().name());
        claims.put("university-id", university.getId());
        String token = jwtService.generateToken(claims, user.getEmail());
        return new AuthResponse(token, "Bearer", user.getEmail(), user.getRole().name(), university.getId());
    }
}
