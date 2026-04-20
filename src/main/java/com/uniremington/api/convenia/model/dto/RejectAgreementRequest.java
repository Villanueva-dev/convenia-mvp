package com.uniremington.api.convenia.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload to reject a practice agreement at any review stage.
 *
 * <p>The rejection reason is stored on the agreement and visible to the student
 * so they understand what corrections are needed before resubmitting.</p>
 *
 * @param reason Human-readable explanation of why the agreement was rejected.
 */
public record RejectAgreementRequest(

        @NotBlank(message = "Rejection reason is required")
        @Size(min = 10, max = 1000, message = "Rejection reason must be between 10 and 1000 characters")
        String reason

) {}
