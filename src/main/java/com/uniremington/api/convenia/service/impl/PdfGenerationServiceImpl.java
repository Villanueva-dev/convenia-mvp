package com.uniremington.api.convenia.service.impl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.uniremington.api.convenia.model.entity.Agreement;
import com.uniremington.api.convenia.service.PdfGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates practice agreement PDFs by processing the Thymeleaf template
 * {@code convenio_practica} and converting the resulting HTML to PDF via OpenHTMLToPDF.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGenerationServiceImpl implements PdfGenerationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TemplateEngine templateEngine;

    /**
     * {@inheritDoc}
     *
     * <p>Builds a Thymeleaf {@link Context} with all agreement parties and dates,
     * processes the {@code convenio_practica} template to HTML, then converts
     * the HTML to PDF bytes using OpenHTMLToPDF's {@link PdfRendererBuilder}.</p>
     */
    @Override
    public byte[] generateAgreementPdf(Agreement agreement) {
        log.info("Generating PDF for agreement id={}", agreement.getId());

        var ctx = buildTemplateContext(agreement);
        String html = templateEngine.process("convenio_practica", ctx);

        try (var outputStream = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();

            byte[] pdfBytes = outputStream.toByteArray();
            log.info("PDF generated successfully for agreement id={}, size={} bytes",
                    agreement.getId(), pdfBytes.length);
            return pdfBytes;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate PDF for agreement id=" + agreement.getId(), e);
        }
    }

    private Context buildTemplateContext(Agreement agreement) {
        var ctx = new Context();

        ctx.setVariable("agreementId",       agreement.getId());
        ctx.setVariable("companyName",        agreement.getCompany().getLegalName());
        ctx.setVariable("companyNit",         agreement.getCompany().getNit());
        ctx.setVariable("companyRepName",     agreement.getCompany().getRepresentativeName());
        ctx.setVariable("studentName",        agreement.getStudent().getFullName());
        ctx.setVariable("studentId",          agreement.getStudent().getDocumentNumber());
        ctx.setVariable("universityName",     agreement.getUniversity().getName());
        ctx.setVariable("programName",        agreement.getStudent().getAcademicProgram().getName());
        ctx.setVariable("practiceModality",   agreement.getPracticeModality().name());
        ctx.setVariable("contractType",       agreement.getContractType().name());
        ctx.setVariable("weeklyHours",        agreement.getWeeklyHours());
        ctx.setVariable("monthlyStipend",     agreement.getMonthlyStipend());
        ctx.setVariable("startDate",          formatDate(agreement.getStartDate()));
        ctx.setVariable("endDate",            formatDate(agreement.getEndDate()));
        ctx.setVariable("currentDate",        formatDate(LocalDate.now()));

        if (agreement.getAcademicAdvisor() != null) {
            ctx.setVariable("advisorName",  agreement.getAcademicAdvisor().getEmail());
        }
        if (agreement.getCompanyRep() != null) {
            ctx.setVariable("companyRepUserEmail", agreement.getCompanyRep().getEmail());
        }

        return ctx;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }
}
