package com.uniremington.api.convenia.model.mapper;

import com.uniremington.api.convenia.model.dto.AgreementResponse;
import com.uniremington.api.convenia.model.dto.AgreementSummaryResponse;
import com.uniremington.api.convenia.model.entity.Agreement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for converting between {@link Agreement} entities and DTOs.
 *
 * <p>Handles all nested field projections (student name, company name, advisor email)
 * and enum-to-String conversions required by the response records.</p>
 */
@Mapper(componentModel = "spring")
public interface AgreementMapper {

    /**
     * Converts an {@link Agreement} entity to its full {@link AgreementResponse} DTO.
     *
     * @param agreement The source entity (must have student and company eagerly loaded).
     * @return The fully mapped response record.
     */
    @Mapping(target = "universityId",         source = "university.id")
    @Mapping(target = "studentId",            source = "student.id")
    @Mapping(target = "studentName",          source = "student.fullName")
    @Mapping(target = "companyId",            source = "company.id")
    @Mapping(target = "companyName",          source = "company.legalName")
    @Mapping(target = "academicAdvisorId",    expression = "java(agreement.getAcademicAdvisor() != null ? agreement.getAcademicAdvisor().getId() : null)")
    @Mapping(target = "academicAdvisorEmail", expression = "java(agreement.getAcademicAdvisor() != null ? agreement.getAcademicAdvisor().getEmail() : null)")
    @Mapping(target = "companyRepId",         expression = "java(agreement.getCompanyRep() != null ? agreement.getCompanyRep().getId() : null)")
    @Mapping(target = "companyRepEmail",      expression = "java(agreement.getCompanyRep() != null ? agreement.getCompanyRep().getEmail() : null)")
    @Mapping(target = "practiceModality",     expression = "java(agreement.getPracticeModality().name())")
    @Mapping(target = "practiceComponent",    expression = "java(agreement.getPracticeComponent().name())")
    @Mapping(target = "contractType",         expression = "java(agreement.getContractType().name())")
    @Mapping(target = "status",               expression = "java(agreement.getStatus().name())")
    @Mapping(target = "certificateApprovedBy", expression = "java(agreement.getCertificateApprovedBy() != null ? agreement.getCertificateApprovedBy().getId() : null)")
    AgreementResponse toResponse(Agreement agreement);

    /**
     * Converts an {@link Agreement} entity to a lightweight {@link AgreementSummaryResponse}
     * suitable for list views.
     *
     * @param agreement The source entity.
     * @return The mapped summary record.
     */
    @Mapping(target = "studentName", source = "student.fullName")
    @Mapping(target = "companyName", source = "company.legalName")
    @Mapping(target = "status",      expression = "java(agreement.getStatus().name())")
    AgreementSummaryResponse toSummary(Agreement agreement);
}
