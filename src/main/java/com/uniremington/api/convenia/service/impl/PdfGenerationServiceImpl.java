package com.uniremington.api.convenia.service.impl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.uniremington.api.convenia.model.entity.Agreement;
import com.uniremington.api.convenia.model.entity.ContractType;
import com.uniremington.api.convenia.model.entity.PracticeComponent;
import com.uniremington.api.convenia.model.entity.PracticeModality;
import com.uniremington.api.convenia.service.PdfGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGenerationServiceImpl implements PdfGenerationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TemplateEngine templateEngine;

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
            log.info("PDF generated for agreement id={}, size={} bytes", agreement.getId(), pdfBytes.length);
            return pdfBytes;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate PDF for agreement id=" + agreement.getId(), e);
        }
    }

    private Context buildTemplateContext(Agreement agreement) {
        var ctx = new Context();

        // Agreement
        ctx.setVariable("agreementId",           agreement.getId());

        // University
        ctx.setVariable("universityName",         agreement.getUniversity().getName());

        // Company
        ctx.setVariable("companyName",            agreement.getCompany().getLegalName());
        ctx.setVariable("companyNit",             agreement.getCompany().getNit());
        ctx.setVariable("companyRepName",         agreement.getCompany().getRepresentativeName());

        // Student
        var student = agreement.getStudent();
        ctx.setVariable("studentName",            student.getFullName());
        ctx.setVariable("studentId",              student.getDocumentNumber());
        ctx.setVariable("studentEmail",           student.getUser().getEmail());
        ctx.setVariable("studentPhone",           student.getPhoneNumber());
        ctx.setVariable("programName",            student.getAcademicProgram().getName());

        // Enum display labels (Spanish, per Resolución 002-2024)
        ctx.setVariable("practiceModalityLabel",  modalityLabel(agreement.getPracticeModality()));
        ctx.setVariable("practiceComponentLabel", componentLabel(agreement.getPracticeComponent()));
        ctx.setVariable("contractTypeLabel",      contractTypeLabel(agreement.getContractType()));

        // Dates and hours
        ctx.setVariable("startDate",              formatDate(agreement.getStartDate()));
        ctx.setVariable("endDate",                formatDate(agreement.getEndDate()));
        ctx.setVariable("weeklyHours",            agreement.getWeeklyHours());
        ctx.setVariable("monthlyStipend",         agreement.getMonthlyStipend());
        ctx.setVariable("currentDate",            formatDate(LocalDate.now()));

        // Advisor — User entity only stores email; full name requires a profile entity
        if (agreement.getAcademicAdvisor() != null) {
            ctx.setVariable("advisorEmail", agreement.getAcademicAdvisor().getEmail());
        }

        // Company tutor — same limitation, email only
        if (agreement.getCompanyRep() != null) {
            ctx.setVariable("companyTutorEmail", agreement.getCompanyRep().getEmail());
        }

        return ctx;
    }

    private String modalityLabel(PracticeModality modality) {
        return switch (modality) {
            case PROFESSIONAL  -> "Práctica Profesional";
            case SOCIAL        -> "Práctica Social";
            case RESEARCH      -> "Práctica Investigativa";
            case INTERNATIONAL -> "Práctica Internacional";
        };
    }

    private String componentLabel(PracticeComponent component) {
        return switch (component) {
            case ACADEMIC   -> "Componente Académico";
            case SOCIAL     -> "Componente Social";
            case MANAGEMENT -> "Componente de Gestión";
        };
    }

    private String contractTypeLabel(ContractType contractType) {
        return switch (contractType) {
            case EMPLOYMENT           -> "Contrato Laboral";
            case APPRENTICESHIP       -> "Contrato de Aprendizaje (SENA/SGVA)";
            case INTERNSHIP_AGREEMENT -> "Convenio Específico de Pasantía";
            case FRAMEWORK_AGREEMENT  -> "Convenio Marco";
        };
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : "";
    }
}
