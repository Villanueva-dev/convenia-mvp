package com.uniremington.api.convenia.repository;

import com.uniremington.api.convenia.model.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for {@link Student} entities.
 */
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByUserId(Long userId);

    List<Student> findByUniversityIdOrderByFullNameAsc(Long universityId);
}
