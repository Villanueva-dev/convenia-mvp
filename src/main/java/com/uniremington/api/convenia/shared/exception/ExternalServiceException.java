package com.uniremington.api.convenia.shared.exception;

/**
 * Thrown when an external dependency (Documenso, Cloudflare R2) returns an
 * unexpected response or is unreachable.
 *
 * <p>Caught by {@link GlobalExceptionHandler} and returned as {@code 502 Bad Gateway}
 * so callers know the failure is upstream, not in their request.</p>
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
