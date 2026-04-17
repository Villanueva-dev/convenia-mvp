package com.uniremington.api.convenia.service;

import com.uniremington.api.convenia.model.dto.AuthResponse;
import com.uniremington.api.convenia.model.dto.LoginRequest;

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
}
