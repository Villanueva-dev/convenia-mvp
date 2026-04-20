package com.uniremington.api.convenia.controller;

import com.uniremington.api.convenia.model.dto.StudentSummaryResponse;
import com.uniremington.api.convenia.model.vo.JwtUser;
import com.uniremington.api.convenia.repository.StudentRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Students", description = "Student directory")
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

    private final StudentRepository studentRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN', 'ACADEMIC_ADVISOR')")
    public ResponseEntity<List<StudentSummaryResponse>> listStudents(@AuthenticationPrincipal JwtUser currentUser) {
        var students = studentRepository.findByUniversityIdOrderByFullNameAsc(currentUser.getUniversityId());
        return ResponseEntity.ok(students.stream().map(StudentSummaryResponse::from).toList());
    }
}
