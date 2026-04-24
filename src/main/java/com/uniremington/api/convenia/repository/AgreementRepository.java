package com.uniremington.api.convenia.repository;

import com.uniremington.api.convenia.model.entity.Agreement;
import com.uniremington.api.convenia.model.entity.AgreementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for {@link Agreement} entities.
 */
public interface AgreementRepository extends JpaRepository<Agreement, Long> {

    /**
     * Retrieves all agreements belonging to a specific university tenant,
     * ordered by creation date descending.
     *
     * @param universityId The tenant university ID.
     * @return List of agreements for the given university.
     */
    List<Agreement> findByUniversityIdOrderByCreatedAtDesc(Long universityId);

    /**
     * Retrieves all agreements for a given student, ordered by creation date descending.
     *
     * @param studentId The student's unique identifier.
     * @return List of agreements associated with the student.
     */
    List<Agreement> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    /**
     * Retrieves all agreements where the given user is the assigned academic advisor,
     * ordered by creation date descending. Used to scope list views for the
     * {@code ACADEMIC_ADVISOR} role so advisors only see their own assignments.
     *
     * @param academicAdvisorId The user ID of the assigned advisor.
     * @return List of agreements where the user is the assigned advisor.
     */
    List<Agreement> findByAcademicAdvisorIdOrderByCreatedAtDesc(Long academicAdvisorId);

    /**
     * Retrieves all agreements where the given user is the assigned company
     * representative, ordered by creation date descending. Used to scope list
     * views for the {@code COMPANY_TUTOR} role.
     *
     * @param companyRepId The user ID of the assigned company tutor.
     * @return List of agreements where the user is the assigned tutor.
     */
    List<Agreement> findByCompanyRepIdOrderByCreatedAtDesc(Long companyRepId);

    /**
     * Retrieves all agreements within a university filtered by status.
     *
     * @param universityId The tenant university ID.
     * @param status       The agreement status to filter by.
     * @return Filtered list of agreements.
     */
    List<Agreement> findByUniversityIdAndStatusOrderByCreatedAtDesc(Long universityId, AgreementStatus status);

    /**
     * Finds an agreement by its Documenso document ID.
     *
     * <p>Used by the signature webhook handler to locate the agreement
     * when Documenso notifies that a document has been fully signed.</p>
     *
     * @param documensoDocumentId The Documenso document ID stored when the agreement was sent for signing.
     * @return The matching agreement, or empty if not found.
     */
    Optional<Agreement> findByDocumensoDocumentId(String documensoDocumentId);
}
