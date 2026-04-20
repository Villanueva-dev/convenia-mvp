package com.uniremington.api.convenia.repository;

import com.uniremington.api.convenia.model.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for {@link Company} entities.
 */
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByIdAndUniversityId(Long id, Long universityId);

    List<Company> findByUniversityIdOrderByLegalNameAsc(Long universityId);

    boolean existsByNitAndUniversityId(String nit, Long universityId);
}
