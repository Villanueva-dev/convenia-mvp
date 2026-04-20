package com.uniremington.api.convenia.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload posted by Documenso to the webhook endpoint on signing events.
 *
 * <p>Documenso v2 wraps the document under the key {@code payload} (not {@code data}).
 * The signed PDF is NOT included — retrieve it via {@code GET /envelope/download-item}
 * using the envelope ID stored in {@code payload.id}.</p>
 *
 * <p>Envelope source code:
 * {@code packages/lib/jobs/definitions/internal/execute-webhook.handler.ts}</p>
 *
 * @param event           Event type (e.g., {@code "DOCUMENT_COMPLETED"}).
 * @param createdAt       ISO-8601 timestamp of when the event was emitted.
 * @param webhookEndpoint The URL this payload was sent to.
 * @param payload         Document data associated with the event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SignatureWebhookPayload(
        String event,
        String createdAt,
        String webhookEndpoint,
        WebhookDocumentData payload
) {

    /**
     * Document-level data included in the Documenso webhook event.
     *
     * @param id         Documenso envelope ID (numeric). Used to match back to our agreement.
     * @param externalId Our identifier set when creating the document (e.g., "agreement-42").
     * @param status     Document status (e.g., {@code "COMPLETED"}).
     * @param completedAt ISO-8601 timestamp when all parties finished signing.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookDocumentData(
            Long id,
            String externalId,
            String status,
            String completedAt
    ) {}
}
