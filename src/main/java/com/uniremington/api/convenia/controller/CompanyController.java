package com.uniremington.api.convenia.controller;

import com.uniremington.api.convenia.model.dto.CompanyResponse;
import com.uniremington.api.convenia.model.dto.CreateCompanyRequest;
import com.uniremington.api.convenia.model.entity.Company;
import com.uniremington.api.convenia.model.entity.University;
import com.uniremington.api.convenia.model.vo.JwtUser;
import com.uniremington.api.convenia.repository.CompanyRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Companies", description = "Company directory management")
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {

    private final CompanyRepository companyRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN', 'ACADEMIC_ADVISOR')")
    public ResponseEntity<List<CompanyResponse>> listCompanies(@AuthenticationPrincipal JwtUser currentUser) {
        var companies = companyRepository.findByUniversityIdOrderByLegalNameAsc(currentUser.getUniversityId());
        return ResponseEntity.ok(companies.stream().map(CompanyResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<CompanyResponse> createCompany(
            @Valid @RequestBody CreateCompanyRequest request,
            @AuthenticationPrincipal JwtUser currentUser) {

        if (companyRepository.existsByNitAndUniversityId(request.nit(), currentUser.getUniversityId())) {
            throw new IllegalArgumentException("A company with NIT " + request.nit() + " already exists");
        }

        var university = new University();
        university.setId(currentUser.getUniversityId());

        var company = Company.builder()
                .university(university)
                .legalName(request.legalName())
                .nit(request.nit())
                .representativeName(request.representativeName())
                .representativeEmail(request.representativeEmail())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CompanyResponse.from(companyRepository.save(company)));
    }
}
