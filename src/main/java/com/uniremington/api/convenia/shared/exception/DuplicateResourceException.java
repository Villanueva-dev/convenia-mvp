package com.uniremington.api.convenia.shared.exception;

/**
 * Thrown when an attempt is made to create a resource that already exists
 * (e.g., duplicate email, duplicate NIT).
 *
 * <p>Caught by {@link GlobalExceptionHandler} and returned as {@code 409 Conflict}.</p>
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
