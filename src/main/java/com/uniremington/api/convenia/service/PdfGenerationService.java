package com.uniremington.api.convenia.service;

import com.uniremington.api.convenia.model.entity.Agreement;

/**
 * Generates PDF documents for professional practice agreements.
 */
public interface PdfGenerationService {

    /**
     * Generates the practice agreement PDF from the Thymeleaf template,
     * populating it with data from the given agreement entity.
     *
     * <p>The generated PDF is intended to be sent to Documenso for digital
     * signing by all parties (student, company representative, coordinator).</p>
     *
     * @param agreement The fully-loaded agreement entity with all related parties.
     * @return Raw PDF bytes ready to be uploaded or stored.
     * @throws RuntimeException if template processing or PDF conversion fails.
     */
    byte[] generateAgreementPdf(Agreement agreement);

    /**
     * Generates the "Constancia de Culminación" PDF issued by the university
     * once the agreement reaches {@code FINISHED}. Includes student, company,
     * advisor and tutor names, practice dates, total hours and final grade,
     * citing Resolución CF 002-2024 Art. §761.
     *
     * @param agreement A FINISHED agreement with both grades and finalGrade populated.
     * @return Raw PDF bytes.
     * @throws RuntimeException if template processing or PDF conversion fails.
     */
    byte[] generateCertificatePdf(Agreement agreement);
}
