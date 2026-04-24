package com.uniremington.api.convenia.model.dto;

import com.uniremington.api.convenia.model.entity.University;

/**
 * Lightweight projection of a {@link University} for dropdowns / pickers.
 * Exposes only the fields a client needs to let a user choose one.
 */
public record UniversitySummaryResponse(
        Long id,
        String name,
        String shortName,
        String city
) {
    public static UniversitySummaryResponse from(University u) {
        return new UniversitySummaryResponse(u.getId(), u.getName(), u.getShortName(), u.getCity());
    }
}
