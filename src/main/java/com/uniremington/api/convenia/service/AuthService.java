package com.uniremington.api.convenia.service;

import com.uniremington.api.convenia.model.dto.AuthResponse;
import com.uniremington.api.convenia.model.dto.LoginRequest;
import com.uniremington.api.convenia.model.dto.RegisterRequest;
import com.uniremington.api.convenia.model.entity.UserRole;

/**
 * Contract for authentication operations.
 */
public interface AuthService {

    /**
     * Authenticates a user and issues a JWT.
     *
     * @param request the login credentials (email + password)
     * @return an {@link AuthResponse} containing the signed JWT and user metadata
     * @throws com.uniremington.api.convenia.shared.exception.InvalidCredentialsException
     *         if the credentials are invalid
     */
    AuthResponse login(LoginRequest request);

    /**
     * Registers a new user and returns a JWT (same as logging in immediately).
     * Allowed roles: STUDENT, ACADEMIC_ADVISOR, COMPANY_TUTOR.
     */
    AuthResponse register(RegisterRequest request, UserRole role);
}
