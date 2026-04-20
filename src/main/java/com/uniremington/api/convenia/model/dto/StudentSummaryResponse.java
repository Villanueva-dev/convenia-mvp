package com.uniremington.api.convenia.model.dto;

import com.uniremington.api.convenia.model.entity.Student;

public record StudentSummaryResponse(
        Long id,
        String fullName,
        String documentNumber,
        String email,
        String academicProgram,
        Integer currentSemester
) {
    public static StudentSummaryResponse from(Student s) {
        return new StudentSummaryResponse(
                s.getId(),
                s.getFullName(),
                s.getDocumentNumber(),
                s.getUser().getEmail(),
                s.getAcademicProgram().getName(),
                s.getCurrentSemester()
        );
    }
}
