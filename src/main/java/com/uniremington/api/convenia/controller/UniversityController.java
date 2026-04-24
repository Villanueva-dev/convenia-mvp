package com.uniremington.api.convenia.controller;

import com.uniremington.api.convenia.model.dto.UniversitySummaryResponse;
import com.uniremington.api.convenia.repository.UniversityRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes the list of universities available in the platform.
 *
 * <p>Open to any authenticated user so ADMIN can populate a selector when
 * creating cross-tenant accounts, and future flows (e.g., public programs)
 * can reuse the same shape.</p>
 */
@Tag(name = "Universities", description = "University directory (read-only)")
@RestController
@RequestMapping("/api/v1/universities")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UniversityController {

    private final UniversityRepository universityRepository;

    @Operation(summary = "List universities",
            description = "Returns every university in the platform, sorted by name. "
                    + "Used by ADMIN to select the tenant when creating user accounts.")
    @GetMapping
    public ResponseEntity<List<UniversitySummaryResponse>> listUniversities() {
        return ResponseEntity.ok(
                universityRepository.findAll().stream()
                        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                        .map(UniversitySummaryResponse::from)
                        .toList()
        );
    }
}
