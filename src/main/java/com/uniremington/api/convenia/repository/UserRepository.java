package com.uniremington.api.convenia.repository;

import com.uniremington.api.convenia.model.entity.User;
import com.uniremington.api.convenia.model.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for {@link User} entities.
 *
 * <p>
 * Spring Data JPA auto-generates the implementation at startup.
 * No {@code @Repository} annotation is needed when extending {@code JpaRepository}.
 * </p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their email address.
     * Used during login and JWT authentication.
     *
     * @param email the email to search for
     * @return an {@code Optional} containing the user, or empty if not found
     */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByUniversityIdAndRoleOrderByEmailAsc(Long universityId, UserRole role);
}
