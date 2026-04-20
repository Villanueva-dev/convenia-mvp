package com.uniremington.api.convenia.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Verifies incoming Documenso webhook requests by comparing the
 * {@code X-Documenso-Secret} header against the configured webhook secret.
 *
 * <p>Documenso does NOT use HMAC — it sends the webhook secret as a plain
 * string in the {@code X-Documenso-Secret} header. Comparison is done with
 * {@link MessageDigest#isEqual} for constant-time equality (prevents timing attacks).</p>
 *
 * <p>If {@code app.documenso.webhook-secret} is blank, verification is skipped
 * with a warning — safe for local development, unsafe in production.</p>
 */
@Slf4j
@Component
public class DocumensoWebhookVerifier {

    @Value("${app.documenso.webhook-secret:}")
    private String webhookSecret;

    /**
     * Returns {@code true} if the received secret matches the configured one.
     *
     * @param receivedSecret Value of the {@code X-Documenso-Secret} header.
     */
    public boolean verify(String receivedSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("DOCUMENSO_WEBHOOK_SECRET is not set — skipping verification. Do NOT use in production.");
            return true;
        }

        if (receivedSecret == null || receivedSecret.isBlank()) {
            log.warn("X-Documenso-Secret header is missing or empty");
            return false;
        }

        boolean valid = MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                receivedSecret.getBytes(StandardCharsets.UTF_8)
        );

        if (!valid) {
            log.warn("Documenso webhook secret mismatch — request rejected");
        }

        return valid;
    }
}
