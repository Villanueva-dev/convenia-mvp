package com.uniremington.api.convenia.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniremington.api.convenia.model.entity.Agreement;
import com.uniremington.api.convenia.service.DocumensoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Integrates with the Documenso REST API v2.
 *
 * <p><b>Template mode</b> (when {@code app.documenso.template-id} is set):
 * <ol>
 *   <li>{@code GET /envelope/{templateId}} — fetches template recipient IDs.</li>
 *   <li>{@code POST /envelope/use} — multipart: {@code payload} JSON (maps real emails to
 *       template recipient slots, sets externalId, distributeDocument=true).</li>
 * </ol>
 * No PDF upload needed; signature fields come from the template.
 *
 * <p><b>Direct mode</b> (when no template ID is configured):
 * <ol>
 *   <li>{@code POST /envelope/create} — multipart: {@code payload} JSON + {@code files} PDF bytes.</li>
 *   <li>{@code POST /envelope/distribute} — sends signing invitations.</li>
 * </ol>
 */
@Slf4j
@Service
public class DocumensoServiceImpl implements DocumensoService {

    private static final ObjectMapper MAPPER        = new ObjectMapper();
    private static final String       SIGNER_ROLE   = "SIGNER";
    private static final String       DOCUMENT_TYPE = "DOCUMENT";

    // Signature field geometry for direct mode (% of page width/height, 0-100)
    private static final double SIG_WIDTH     = 25.0;
    private static final double SIG_HEIGHT    = 8.0;
    private static final double SIG_Y         = 84.0;
    private static final double SIG_X_ADVISOR = 5.0;
    private static final double SIG_X_COMPANY = 37.0;
    private static final double SIG_X_STUDENT = 69.0;

    private final RestClient documensoRestClient;
    private final String     templateId;

    public DocumensoServiceImpl(
            RestClient documensoRestClient,
            @Value("${app.documenso.template-id:}") String templateId) {
        this.documensoRestClient = documensoRestClient;
        this.templateId = (templateId != null && !templateId.isBlank()) ? templateId : null;
    }

    @Override
    public boolean isTemplateMode() {
        return templateId != null;
    }

    @Override
    public String sendForSignature(Agreement agreement, byte[] pdfBytes) {
        log.info("Sending agreement id={} to Documenso (templateMode={})", agreement.getId(), isTemplateMode());
        String envelopeId = isTemplateMode()
                ? sendViaTemplate(agreement)
                : sendViaPdfUpload(agreement, pdfBytes);
        log.info("Agreement id={} sent to Documenso, envelopeId={}", agreement.getId(), envelopeId);
        return envelopeId;
    }

    // ── Template mode ─────────────────────────────────────────────────────────

    private String sendViaTemplate(Agreement agreement) {
        List<TemplateRecipient> templateRecipients = fetchTemplateRecipients(templateId);
        log.debug("Template {} has {} recipients", templateId, templateRecipients.size());

        String payloadJson = serializePayload(buildUsePayload(agreement, templateRecipients));

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("payload", payloadJson);

        var response = documensoRestClient.post()
                .uri("/envelope/use")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(UseEnvelopeResponse.class);

        Objects.requireNonNull(response, "Documenso /envelope/use returned null response");
        log.debug("Envelope created from template: id={}", response.id());
        return response.id();
    }

    private List<TemplateRecipient> fetchTemplateRecipients(String templateId) {
        var response = documensoRestClient.get()
                .uri("/envelope/" + templateId)
                .retrieve()
                .body(GetEnvelopeResponse.class);

        Objects.requireNonNull(response, "Documenso GET /envelope/" + templateId + " returned null");
        if (response.recipients() == null || response.recipients().isEmpty()) {
            throw new IllegalStateException("Documenso template " + templateId + " has no recipients configured");
        }
        return response.recipients();
    }

    private Map<String, Object> buildUsePayload(Agreement agreement, List<TemplateRecipient> templateRecipients) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("envelopeId",         templateId);
        payload.put("externalId",         "agreement-" + agreement.getId());
        payload.put("distributeDocument", true);
        payload.put("recipients",         buildTemplateRecipientMappings(agreement, templateRecipients));
        payload.put("override", Map.of(
                "subject", "Convenio de Práctica Profesional — " + agreement.getUniversity().getName(),
                "message", "Por favor revise y firme el convenio de práctica profesional."
        ));
        return payload;
    }

    /**
     * Maps the template's recipient slots (by index order) to real party emails.
     * Template slot order assumed: 0=advisor, 1=companyRep, 2=student.
     */
    private List<Map<String, Object>> buildTemplateRecipientMappings(
            Agreement agreement, List<TemplateRecipient> slots) {

        record PartyInfo(String name, String email) {}

        var parties = new ArrayList<PartyInfo>();

        if (agreement.getAcademicAdvisor() != null) {
            parties.add(new PartyInfo(
                    agreement.getAcademicAdvisor().getEmail(),
                    agreement.getAcademicAdvisor().getEmail()));
        }
        if (agreement.getCompanyRep() != null) {
            parties.add(new PartyInfo(
                    agreement.getCompanyRep().getEmail(),
                    agreement.getCompanyRep().getEmail()));
        }
        parties.add(new PartyInfo(
                agreement.getStudent().getFullName(),
                agreement.getStudent().getUser().getEmail()));

        var mappings = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < Math.min(parties.size(), slots.size()); i++) {
            var party = parties.get(i);
            var slot  = slots.get(i);
            var entry = new LinkedHashMap<String, Object>();
            entry.put("id",    slot.id());
            entry.put("name",  party.name());
            entry.put("email", party.email());
            mappings.add(entry);
        }
        return mappings;
    }

    // ── Direct PDF-upload mode ─────────────────────────────────────────────────

    private String sendViaPdfUpload(Agreement agreement, byte[] pdfBytes) {
        Objects.requireNonNull(pdfBytes, "pdfBytes required in direct (non-template) mode");
        String envelopeId = createEnvelope(agreement, pdfBytes);
        distribute(envelopeId);
        return envelopeId;
    }

    private String createEnvelope(Agreement agreement, byte[] pdfBytes) {
        String payloadJson = serializePayload(buildEnvelopePayload(agreement));

        var body = new LinkedMultiValueMap<String, Object>();
        body.add("payload", payloadJson);
        body.add("files", new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "convenio_practica_" + agreement.getId() + ".pdf";
            }
        });

        var response = documensoRestClient.post()
                .uri("/envelope/create")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(CreateEnvelopeResponse.class);

        Objects.requireNonNull(response, "Documenso /envelope/create returned null response");
        log.debug("Envelope created: id={}", response.id());
        return response.id();
    }

    private Map<String, Object> buildEnvelopePayload(Agreement agreement) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("title",      "Convenio de Práctica — " + agreement.getStudent().getFullName());
        payload.put("type",       DOCUMENT_TYPE);
        payload.put("externalId", "agreement-" + agreement.getId());
        payload.put("meta",       buildMeta(agreement));
        payload.put("recipients", buildRecipients(agreement));
        return payload;
    }

    private Map<String, Object> buildMeta(Agreement agreement) {
        var meta = new LinkedHashMap<String, Object>();
        meta.put("subject", "Convenio de Práctica Profesional — " + agreement.getUniversity().getName());
        meta.put("message", "Por favor revise y firme el convenio de práctica profesional adjunto.");
        meta.put("signingOrder", "PARALLEL");
        return meta;
    }

    private List<Map<String, Object>> buildRecipients(Agreement agreement) {
        var recipients = new ArrayList<Map<String, Object>>();

        if (agreement.getAcademicAdvisor() != null) {
            recipients.add(recipientEntry(
                    agreement.getAcademicAdvisor().getEmail(),
                    agreement.getAcademicAdvisor().getEmail(),
                    SIG_X_ADVISOR));
        }
        if (agreement.getCompanyRep() != null) {
            recipients.add(recipientEntry(
                    agreement.getCompanyRep().getEmail(),
                    agreement.getCompanyRep().getEmail(),
                    SIG_X_COMPANY));
        }
        recipients.add(recipientEntry(
                agreement.getStudent().getFullName(),
                agreement.getStudent().getUser().getEmail(),
                SIG_X_STUDENT));

        return recipients;
    }

    private Map<String, Object> recipientEntry(String name, String email, double sigX) {
        var recipient = new LinkedHashMap<String, Object>();
        recipient.put("name",   name);
        recipient.put("email",  email);
        recipient.put("role",   SIGNER_ROLE);
        recipient.put("fields", List.of(signatureField(sigX)));
        return recipient;
    }

    private Map<String, Object> signatureField(double positionX) {
        var field = new LinkedHashMap<String, Object>();
        field.put("type",      "SIGNATURE");
        field.put("page",      1);
        field.put("positionX", positionX);
        field.put("positionY", SIG_Y);
        field.put("width",     SIG_WIDTH);
        field.put("height",    SIG_HEIGHT);
        return field;
    }

    private void distribute(String envelopeId) {
        documensoRestClient.post()
                .uri("/envelope/distribute")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("envelopeId", envelopeId))
                .retrieve()
                .toBodilessEntity();

        log.debug("Envelope id={} distributed — signing invitations sent", envelopeId);
    }

    // ── Download signed PDF ───────────────────────────────────────────────────

    @Override
    public byte[] downloadSignedPdf(String envelopeId) {
        log.info("Downloading signed PDF for envelopeId={}", envelopeId);

        var envelope = documensoRestClient.get()
                .uri("/envelope/" + envelopeId)
                .retrieve()
                .body(EnvelopeWithItems.class);

        Objects.requireNonNull(envelope, "GET /envelope/" + envelopeId + " returned null");

        if (envelope.envelopeItems() == null || envelope.envelopeItems().isEmpty()) {
            throw new IllegalStateException("Envelope " + envelopeId + " has no items to download");
        }

        String itemId = envelope.envelopeItems().get(0).id();

        byte[] pdfBytes = documensoRestClient.get()
                .uri("/envelope/item/" + itemId + "/download?version=signed")
                .retrieve()
                .body(byte[].class);

        Objects.requireNonNull(pdfBytes, "Download of envelope item " + itemId + " returned null");
        log.info("Downloaded signed PDF: {} bytes for envelopeId={}", pdfBytes.length, envelopeId);
        return pdfBytes;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String serializePayload(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Documenso envelope payload", e);
        }
    }

    // ── Internal response / data DTOs ─────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreateEnvelopeResponse(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UseEnvelopeResponse(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GetEnvelopeResponse(String id, List<TemplateRecipient> recipients) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TemplateRecipient(Long id, String name, String email) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EnvelopeWithItems(String id, List<EnvelopeItem> envelopeItems) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EnvelopeItem(String id, String title) {}
}
