package com.uniremington.api.convenia.service.impl;

import com.uniremington.api.convenia.model.dto.AllowedRole;
import com.uniremington.api.convenia.model.dto.CreateUserRequest;
import com.uniremington.api.convenia.model.entity.UserRole;
import com.uniremington.api.convenia.repository.UniversityRepository;
import com.uniremington.api.convenia.repository.UserRepository;
import com.uniremington.api.convenia.shared.exception.DuplicateResourceException;
import com.uniremington.api.convenia.shared.exception.ResourceNotFoundException;
import com.uniremington.api.convenia.util.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepository       userRepository;
    @Mock UniversityRepository universityRepository;
    @Mock PasswordEncoder      passwordEncoder;

    @InjectMocks UserServiceImpl service;

    // ── createManagedUser ─────────────────────────────────────────────────────

    @Test
    void coordinatorUsesOwnUniversityId() {
        var university  = TestFixtures.university(1L);
        var coordinator = TestFixtures.jwtUser(5L, "COORDINATOR", 1L);
        var request     = new CreateUserRequest("Test Advisor", "advisor@test.edu.co", "password1", 99L, AllowedRole.ACADEMIC_ADVISOR);
        var savedUser   = TestFixtures.user(50L, UserRole.ACADEMIC_ADVISOR, university);

        when(userRepository.existsByEmail("advisor@test.edu.co")).thenReturn(false);
        when(universityRepository.findById(1L)).thenReturn(Optional.of(university));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser);

        var result = service.createManagedUser(request, coordinator);

        assertThat(result.email()).isEqualTo(savedUser.getEmail());
        verify(universityRepository).findById(1L);
    }

    @Test
    void adminUsesRequestUniversityId() {
        var university = TestFixtures.university(7L);
        var admin      = TestFixtures.jwtUser(1L, "ADMIN", null);
        var request    = new CreateUserRequest("Test Tutor", "tutor@other.edu.co", "password1", 7L, AllowedRole.COMPANY_TUTOR);
        var savedUser  = TestFixtures.user(60L, UserRole.COMPANY_TUTOR, university);

        when(userRepository.existsByEmail("tutor@other.edu.co")).thenReturn(false);
        when(universityRepository.findById(7L)).thenReturn(Optional.of(university));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser);

        service.createManagedUser(request, admin);

        verify(universityRepository).findById(7L);
    }

    @Test
    void throwsWhenEmailAlreadyExists() {
        var coordinator = TestFixtures.jwtUser(5L, "COORDINATOR", 1L);
        var request     = new CreateUserRequest("Test Advisor", "existing@test.edu.co", "password1", 1L, AllowedRole.ACADEMIC_ADVISOR);

        when(userRepository.existsByEmail("existing@test.edu.co")).thenReturn(true);

        assertThatThrownBy(() -> service.createManagedUser(request, coordinator))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(universityRepository, never()).findById(any());
    }

    @Test
    void throwsWhenUniversityNotFound() {
        var coordinator = TestFixtures.jwtUser(5L, "COORDINATOR", 99L);
        var request     = new CreateUserRequest("Test Secretary", "new@test.edu.co", "password1", 99L, AllowedRole.SECRETARY);

        when(userRepository.existsByEmail("new@test.edu.co")).thenReturn(false);
        when(universityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createManagedUser(request, coordinator))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AllowedRole.class, names = {"ACADEMIC_ADVISOR", "COMPANY_TUTOR", "SECRETARY"})
    void coordinatorCanCreateOperationalRoles(AllowedRole allowedRole) {
        var university  = TestFixtures.university(1L);
        var coordinator = TestFixtures.jwtUser(5L, "COORDINATOR", 1L);
        var request     = new CreateUserRequest("Test User", "user@test.edu.co", "password1", 1L, allowedRole);

        var expectedRole = switch (allowedRole) {
            case ACADEMIC_ADVISOR -> UserRole.ACADEMIC_ADVISOR;
            case COMPANY_TUTOR    -> UserRole.COMPANY_TUTOR;
            case SECRETARY        -> UserRole.SECRETARY;
            case COORDINATOR      -> throw new IllegalStateException("not covered here");
        };
        var savedUser = TestFixtures.user(50L, expectedRole, university);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(universityRepository.findById(1L)).thenReturn(Optional.of(university));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser);

        var result = service.createManagedUser(request, coordinator);

        assertThat(result.role()).isEqualTo(expectedRole.name());
    }

    @Test
    void adminCanCreateCoordinator() {
        var university = TestFixtures.university(1L);
        var admin      = TestFixtures.jwtUser(1L, "ADMIN", null);
        var request    = new CreateUserRequest("New Coord", "coord@test.edu.co", "password1", 1L, AllowedRole.COORDINATOR);
        var saved      = TestFixtures.user(60L, UserRole.COORDINATOR, university);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(universityRepository.findById(1L)).thenReturn(Optional.of(university));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(saved);

        var result = service.createManagedUser(request, admin);

        assertThat(result.role()).isEqualTo(UserRole.COORDINATOR.name());
    }

    @Test
    void coordinatorCannotCreateAnotherCoordinator() {
        var coordinator = TestFixtures.jwtUser(5L, "COORDINATOR", 1L);
        var request     = new CreateUserRequest("Would-be Coord", "evil@test.edu.co", "password1", 1L, AllowedRole.COORDINATOR);

        assertThatThrownBy(() -> service.createManagedUser(request, coordinator))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Only ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    void encodesPasswordBeforeSaving() {
        var university  = TestFixtures.university(1L);
        var coordinator = TestFixtures.jwtUser(5L, "COORDINATOR", 1L);
        var request     = new CreateUserRequest("Test User", "new@test.edu.co", "plaintext", 1L, AllowedRole.ACADEMIC_ADVISOR);
        var savedUser   = TestFixtures.user(50L, UserRole.ACADEMIC_ADVISOR, university);

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(universityRepository.findById(1L)).thenReturn(Optional.of(university));
        when(passwordEncoder.encode("plaintext")).thenReturn("$2a$encoded");
        when(userRepository.save(any())).thenReturn(savedUser);

        service.createManagedUser(request, coordinator);

        verify(passwordEncoder).encode("plaintext");
        verify(userRepository).save(argThat(u -> "$2a$encoded".equals(u.getPassword())));
    }
}
