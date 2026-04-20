package com.uniremington.api.convenia.service;

import com.uniremington.api.convenia.model.entity.Agreement;

/**
 * Integration with Documenso for digital document signing.
 */
public interface DocumensoService {

    /**
     * Sends the agreement to Documenso for digital signing.
     *
     * <p>When a template ID is configured, uses the pre-built Documenso template
     * (skipping PDF generation). Otherwise uploads the provided PDF bytes directly.</p>
     *
     * @param agreement The agreement entity containing all party information.
     * @param pdfBytes  Raw PDF bytes; may be {@code null} when in template mode.
     * @return The Documenso envelope ID used to track signing status.
     * @throws RuntimeException if the Documenso API call fails.
     */
    String sendForSignature(Agreement agreement, byte[] pdfBytes);

    /**
     * Returns {@code true} when a Documenso template ID is configured, meaning PDF
     * generation should be skipped and the template flow used instead.
     */
    boolean isTemplateMode();

    /**
     * Downloads the signed PDF for the given Documenso envelope ID.
     *
     * @param envelopeId The Documenso envelope ID stored on the agreement.
     * @return Raw bytes of the signed PDF.
     */
    byte[] downloadSignedPdf(String envelopeId);
}
