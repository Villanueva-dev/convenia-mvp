package com.uniremington.api.convenia.shared.exception;

/**
 * Thrown when a requested resource does not exist in the database.
 *
 * <p>
 * Produces a {@code 404 Not Found} HTTP response when caught by
 * {@link GlobalExceptionHandler}.
 * </p>
 *
 * <p>
 * Usage in service layer:
 * </p>
 * <pre>{@code
 * agreementRepository.findById(id)
 *     .orElseThrow(() -> new ResourceNotFoundException("Agreement", id));
 * }</pre>
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates the exception with a descriptive message.
     *
     * @param entityName the type of resource that was not found (e.g., "Agreement")
     * @param id         the ID that was searched
     */
    public ResourceNotFoundException(String entityName, Object id) {
        super(entityName + " not found with id: " + id);
    }

    /**
     * Creates the exception with a fully custom message.
     * Use this when the lookup key is not a simple ID.
     *
     * @param message the full error description
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
