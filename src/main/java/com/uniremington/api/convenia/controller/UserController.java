package com.uniremington.api.convenia.controller;

import com.uniremington.api.convenia.model.dto.CreateUserRequest;
import com.uniremington.api.convenia.model.dto.UserSummaryResponse;
import com.uniremington.api.convenia.model.entity.UserRole;
import com.uniremington.api.convenia.model.vo.JwtUser;
import com.uniremington.api.convenia.repository.UserRepository;
import com.uniremington.api.convenia.service.UserService;
import com.uniremington.api.convenia.shared.exception.ResourceNotFoundException;
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

@Tag(name = "Users", description = "User directory (for dropdowns)")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;
    private final UserService    userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<List<UserSummaryResponse>> listByRole(
            @RequestParam String role,
            @AuthenticationPrincipal JwtUser currentUser) {

        UserRole userRole;
        try {
            userRole = UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Unknown role: " + role);
        }

        var users = userRepository.findByUniversityIdAndRoleOrderByEmailAsc(
                currentUser.getUniversityId(), userRole);
        return ResponseEntity.ok(users.stream().map(UserSummaryResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<UserSummaryResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal JwtUser currentUser) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createManagedUser(request, currentUser));
    }
}
