package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.model.dto.AllowedRole;
import com.uniremington.api.convenia.model.dto.CreateUserRequest;
import com.uniremington.api.convenia.model.dto.UserSummaryResponse;
import com.uniremington.api.convenia.model.entity.User;
import com.uniremington.api.convenia.model.entity.UserRole;
import com.uniremington.api.convenia.model.vo.JwtUser;
import com.uniremington.api.convenia.repository.UniversityRepository;
import com.uniremington.api.convenia.repository.UserRepository;
import com.uniremington.api.convenia.service.UserService;
import com.uniremington.api.convenia.shared.exception.DuplicateResourceException;
import com.uniremington.api.convenia.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository     userRepository;
    private final UniversityRepository universityRepository;
    private final PasswordEncoder    passwordEncoder;

    @Override
    @Transactional
    public UserSummaryResponse createManagedUser(CreateUserRequest request, JwtUser currentUser) {
        // Hierarchy rule: COORDINATOR accounts can only be created by ADMIN.
        // A COORDINATOR cannot create another COORDINATOR (no peer escalation).
        if (request.role() == AllowedRole.COORDINATOR && !"ADMIN".equals(currentUser.getRole())) {
            throw new AccessDeniedException("Only ADMIN can create COORDINATOR accounts");
        }

        Long universityId = "ADMIN".equals(currentUser.getRole())
                ? request.universityId()
                : currentUser.getUniversityId();

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("A user with email " + request.email() + " already exists");
        }

        var university = universityRepository.findById(universityId)
                .orElseThrow(() -> new ResourceNotFoundException("University", universityId));

        var user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(toUserRole(request.role()))
                .university(university)
                .build();

        return UserSummaryResponse.from(userRepository.save(user));
    }

    private UserRole toUserRole(AllowedRole role) {
        return switch (role) {
            case ACADEMIC_ADVISOR -> UserRole.ACADEMIC_ADVISOR;
            case COMPANY_TUTOR    -> UserRole.COMPANY_TUTOR;
            case SECRETARY        -> UserRole.SECRETARY;
            case COORDINATOR      -> UserRole.COORDINATOR;
        };
    }
}
