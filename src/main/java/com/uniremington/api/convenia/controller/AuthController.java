package com.uniremington.api.convenia.controller;

import com.uniremington.api.convenia.model.dto.AuthResponse;
import com.uniremington.api.convenia.model.dto.LoginRequest;
import com.uniremington.api.convenia.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private final AuthService authService;

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
}
