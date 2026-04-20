package com.uniremington.api.convenia.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * Centralized HTTP error handler for the entire API.
 *
 * <p>
 * Every exception thrown from a controller or service is intercepted here
 * and converted into a structured JSON response following
 * <a href="https://www.rfc-editor.org/rfc/rfc7807">RFC 7807 — Problem Details</a>,
 * natively supported by Spring Boot via {@link ProblemDetail}.
 * </p>
 *
 * <p>
 * The RFC 7807 response format looks like:
 * </p>
 * <pre>{@code
 * {
 *   "type":    "https://api.convenia.app/errors/not-found",
 *   "title":   "Resource not found",
 *   "status":  404,
 *   "detail":  "Agreement not found with id: 42"
 * }
 * }</pre>
 *
 * <p>
 * <strong>Design decision:</strong> stack traces are NEVER sent to the client.
 * They are logged server-side at ERROR level for debugging.
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE_URI = "https://api.convenia.app/errors";

    // ── 400 Bad Request — Validation failures ─────────────────────────────────

    /**
     * Handles validation errors triggered by {@code @Valid} in controllers.
     *
     * <p>
     * When a record field fails a constraint (e.g., {@code @NotBlank}),
     * Spring throws this exception. We collect all failing fields into
     * a map so the client sees every error at once, not just the first one.
     * </p>
     *
     * <p>
     * Example response:
     * </p>
     * <pre>{@code
     * {
     *   "type":   "https://api.convenia.app/errors/validation",
     *   "title":  "Validation failed",
     *   "status": 400,
     *   "detail": "One or more fields failed validation",
     *   "errors": {
     *     "email":    "Email must be a valid email address",
     *     "password": "Password is required"
     *   }
     * }
     * }</pre>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex) {

        log.info("Validation failed: {} field error(s)", ex.getBindingResult().getErrorCount());

        // Collect all field errors into a map: fieldName → errorMessage
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (msg1, msg2) -> msg1 + "; " + msg2,  // merge if same field has 2 errors
                        LinkedHashMap::new                     // preserve insertion order
                ));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more fields failed validation");
        problem.setType(URI.create(BASE_URI + "/validation"));
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);

        return ResponseEntity.badRequest().body(problem);
    }

    // ── 401 Unauthorized — Invalid credentials ────────────────────────────────

    /**
     * Handles failed login attempts (wrong email or password).
     *
     * <p>
     * The message is intentionally generic — never reveal which field was wrong.
     * </p>
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException ex) {

        log.warn("Authentication failure: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage());
        problem.setType(URI.create(BASE_URI + "/unauthorized"));
        problem.setTitle("Authentication failed");

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    // ── 403 Forbidden — Insufficient permissions ──────────────────────────────

    /**
     * Handles {@code @PreAuthorize} denials when the user's role
     * does not have permission to access the requested resource.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {

        log.warn("Access denied: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action");
        problem.setType(URI.create(BASE_URI + "/forbidden"));
        problem.setTitle("Access denied");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    // ── 404 Not Found — Resource does not exist ───────────────────────────────

    /**
     * Handles lookups for agreements, students, companies, etc. that do not exist.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(
            ResourceNotFoundException ex) {

        log.info("Resource not found: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage());
        problem.setType(URI.create(BASE_URI + "/not-found"));
        problem.setTitle("Resource not found");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // ── 409 Conflict — State machine violations ───────────────────────────────

    /**
     * Handles state machine violations thrown as {@link IllegalStateException}.
     *
     * <p>Example: trying to endorse an agreement that is not in COORDINATION_REVIEW status.</p>
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex) {

        log.warn("State machine violation: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage());
        problem.setType(URI.create(BASE_URI + "/conflict"));
        problem.setTitle("Invalid state transition");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // ── 400 Bad Request — Business rule violations ────────────────────────────

    /**
     * Handles business rule violations thrown as {@link IllegalArgumentException}.
     *
     * <p>
     * Example: trying to activate an agreement that is not in PENDING_SIGNATURE status.
     * </p>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex) {

        log.warn("Business rule violation: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage());
        problem.setType(URI.create(BASE_URI + "/bad-request"));
        problem.setTitle("Invalid request");

        return ResponseEntity.badRequest().body(problem);
    }

    // ── 500 Internal Server Error — Unexpected failures ───────────────────────

    /**
     * Catches any exception not handled by the specific handlers above.
     *
     * <p>
     * The stack trace is logged server-side for debugging but is NEVER
     * sent to the client — exposing it would be a security vulnerability.
     * </p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {

        log.error("Unexpected error: {}", ex.getMessage(), ex);  // full stack trace in logs only

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        problem.setType(URI.create(BASE_URI + "/internal-error"));
        problem.setTitle("Internal server error");

        return ResponseEntity.internalServerError().body(problem);
    }
}
