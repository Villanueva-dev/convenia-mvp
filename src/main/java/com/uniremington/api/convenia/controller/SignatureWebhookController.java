package com.uniremington.api.convenia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniremington.api.convenia.config.DocumensoWebhookVerifier;
import com.uniremington.api.convenia.model.dto.SignatureWebhookPayload;
import com.uniremington.api.convenia.service.AgreementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



/**
 * Receives webhook events from Documenso when document signing events occur.
 *
 * <p>This endpoint is publicly accessible (no JWT required) because Documenso
 * calls it from its own servers. It is whitelisted in {@code SecurityConfig}
 * under {@code /api/v1/webhooks/**}.</p>
 *
 * <p>Documenso v2 authenticates webhooks by sending the webhook secret as a
 * plain string in the {@code X-Documenso-Secret} header. Requests that fail
 * this check are rejected with 401 before any business logic runs.</p>
 *
 * <p>On {@code DOCUMENT_COMPLETED}: all parties have signed → the agreement is
 * transitioned to ACTIVE. The signed PDF is not included in the webhook payload;
 * it can be fetched on-demand via {@code GET /envelope/download-item}.</p>
 */
@Slf4j
@Tag(name = "Webhooks", description = "Documenso signature event webhooks")
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class SignatureWebhookController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgreementService         agreementService;
    private final DocumensoWebhookVerifier webhookVerifier;

    /**
     * Handles incoming Documenso signature webhook events.
     *
     * <p>Only {@code DOCUMENT_COMPLETED} events trigger a state transition.
     * All other events are acknowledged (200) but ignored.</p>
     *
     * @param secret  Value of the {@code X-Documenso-Secret} header.
     * @param rawBody Raw JSON body as received from Documenso.
     * @return 200 OK on success, 401 if the secret is invalid, 400 if unparseable.
     */
    @Operation(summary = "Documenso webhook",
            description = "Receives signature events from Documenso. Verified via X-Documenso-Secret. No JWT required.")
    @PostMapping(value = "/signature", consumes = "application/json")
    public ResponseEntity<Void> handleSignatureEvent(
            @RequestHeader(value = "X-Documenso-Secret", required = false) String secret,
            @RequestBody String rawBody) {

        if (!webhookVerifier.verify(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SignatureWebhookPayload webhook;
        try {
            webhook = MAPPER.readValue(rawBody, SignatureWebhookPayload.class);
        } catch (Exception e) {
            log.error("Failed to parse Documenso webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        log.info("Documenso webhook received: event={}, envelopeId={}",
                webhook.event(),
                webhook.payload() != null ? webhook.payload().id() : "null");

        if (webhook.payload() == null) {
            log.warn("Documenso webhook received with null payload — skipping");
            return ResponseEntity.ok().build();
        }

        if ("DOCUMENT_COMPLETED".equals(webhook.event())) {
            String envelopeId = String.valueOf(webhook.payload().id());
            log.info("Processing DOCUMENT_COMPLETED for Documenso envelopeId={}", envelopeId);
            agreementService.activateAgreement(envelopeId);
        } else {
            log.debug("Ignoring Documenso event: {}", webhook.event());
        }

        return ResponseEntity.ok().build();
    }
}
