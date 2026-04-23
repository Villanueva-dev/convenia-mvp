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
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGenerationServiceImpl implements PdfGenerationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TemplateEngine templateEngine;

    @Override
    public byte[] generateAgreementPdf(Agreement agreement) {
        log.info("Generating agreement PDF for id={}", agreement.getId());
        return renderPdf("convenio_practica", buildTemplateContext(agreement), agreement.getId());
    }

    @Override
    public byte[] generateCertificatePdf(Agreement agreement) {
        log.info("Generating certificate PDF for agreement id={}", agreement.getId());
        return renderPdf("constancia_culminacion", buildCertificateContext(agreement), agreement.getId());
    }

    private byte[] renderPdf(String templateName, Context ctx, Long agreementId) {
        String html = templateEngine.process(templateName, ctx);

        try (var outputStream = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();

            byte[] pdfBytes = outputStream.toByteArray();
            log.info("PDF '{}' rendered for agreement id={}, size={} bytes",
                    templateName, agreementId, pdfBytes.length);
            return pdfBytes;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to render PDF '" + templateName + "' for agreement id=" + agreementId, e);
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

        // Advisor — full name and email from User entity
        if (agreement.getAcademicAdvisor() != null) {
            ctx.setVariable("advisorName",  agreement.getAcademicAdvisor().getFullName());
            ctx.setVariable("advisorEmail", agreement.getAcademicAdvisor().getEmail());
        }

        // Company tutor — full name and email from User entity
        if (agreement.getCompanyRep() != null) {
            ctx.setVariable("companyTutorName",  agreement.getCompanyRep().getFullName());
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

    private Context buildCertificateContext(Agreement agreement) {
        var ctx = new Context();

        ctx.setVariable("agreementId",     agreement.getId());
        ctx.setVariable("universityName",  agreement.getUniversity().getName());
        ctx.setVariable("companyName",     agreement.getCompany().getLegalName());
        ctx.setVariable("companyNit",      agreement.getCompany().getNit());

        var student = agreement.getStudent();
        ctx.setVariable("studentName",     student.getFullName());
        ctx.setVariable("studentId",       student.getDocumentNumber());
        ctx.setVariable("programName",     student.getAcademicProgram().getName());

        ctx.setVariable("practiceModalityLabel",  modalityLabel(agreement.getPracticeModality()));
        ctx.setVariable("practiceComponentLabel", componentLabel(agreement.getPracticeComponent()));

        LocalDate start = agreement.getStartDate();
        LocalDate end   = agreement.getEndDate();
        ctx.setVariable("startDate", formatDate(start));
        ctx.setVariable("endDate",   formatDate(end));
        ctx.setVariable("weeklyHours", agreement.getWeeklyHours());

        // Total hours: weeks between dates × weeklyHours.
        long totalHours = 0L;
        if (start != null && end != null && agreement.getWeeklyHours() != null) {
            long days = ChronoUnit.DAYS.between(start, end.plusDays(1));
            double weeks = days / 7.0;
            totalHours = Math.round(weeks * agreement.getWeeklyHours());
        }
        ctx.setVariable("totalHours", totalHours);

        ctx.setVariable("advisorGrade", agreement.getAdvisorGrade());
        ctx.setVariable("companyGrade", agreement.getCompanyGrade());
        ctx.setVariable("finalGrade",   agreement.getFinalGrade());

        if (agreement.getAcademicAdvisor() != null) {
            ctx.setVariable("advisorName",  agreement.getAcademicAdvisor().getFullName());
            ctx.setVariable("advisorEmail", agreement.getAcademicAdvisor().getEmail());
        }
        if (agreement.getCompanyRep() != null) {
            ctx.setVariable("companyTutorName",  agreement.getCompanyRep().getFullName());
            ctx.setVariable("companyTutorEmail", agreement.getCompanyRep().getEmail());
        }

        ctx.setVariable("issueDate", formatDate(LocalDate.now()));

        return ctx;
    }
}
