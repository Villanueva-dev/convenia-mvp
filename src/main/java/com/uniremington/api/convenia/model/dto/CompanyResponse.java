package com.uniremington.api.convenia.model.dto;

import com.uniremington.api.convenia.model.entity.Company;

public record CompanyResponse(
        Long id,
        String legalName,
        String nit,
        String representativeName,
        String representativeEmail
) {
    public static CompanyResponse from(Company c) {
        return new CompanyResponse(c.getId(), c.getLegalName(), c.getNit(),
                c.getRepresentativeName(), c.getRepresentativeEmail());
    }
}
