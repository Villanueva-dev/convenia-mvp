package com.uniremington.api.convenia.controller;

import com.uniremington.api.convenia.model.dto.AuthResponse;
import com.uniremington.api.convenia.model.dto.LoginRequest;
import com.uniremington.api.convenia.model.dto.RegisterRequest;
import com.uniremington.api.convenia.model.entity.AcademicProgram;
import com.uniremington.api.convenia.model.entity.UserRole;
import com.uniremington.api.convenia.repository.AcademicProgramRepository;
import com.uniremington.api.convenia.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Public endpoint for user authentication.
 *
 * <p>
 * This controller is intentionally thin: it receives the request,
 * delegates to {@link AuthService}, and returns the response.
 * Zero business logic lives here — that is the Service's responsibility.
 * </p>
 *
 * <p>Base path: {@code /auth}</p>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService               authService;
    private final AcademicProgramRepository academicProgramRepository;

    /**
     * Authenticates a user and returns a signed JWT.
     *
     * <p>
     * {@code @Valid} triggers Jakarta Bean Validation on the request body.
     * If any constraint fails (e.g., missing email), Spring returns a
     * 400 Bad Request automatically — before the service is ever called.
     * </p>
     *
     * @param request the login credentials
     * @return 200 OK with {@link AuthResponse} containing the Bearer token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register/student")
    public ResponseEntity<AuthResponse> registerStudent(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, UserRole.STUDENT));
    }

    @PostMapping("/register/advisor")
    public ResponseEntity<AuthResponse> registerAdvisor(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, UserRole.ACADEMIC_ADVISOR));
    }

    @PostMapping("/register/company-rep")
    public ResponseEntity<AuthResponse> registerCompanyRep(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, UserRole.COMPANY_REP));
    }

    @GetMapping("/programs/{universityId}")
    public ResponseEntity<List<Map<String, Object>>> listPrograms(@PathVariable Long universityId) {
        var programs = academicProgramRepository.findByUniversityIdAndActiveTrue(universityId)
                .stream()
                .map(p -> Map.<String, Object>of("id", p.getId(), "name", p.getName()))
                .toList();
        return ResponseEntity.ok(programs);
    }
}
