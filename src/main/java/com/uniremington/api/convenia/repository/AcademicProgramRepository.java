package com.uniremington.api.convenia.repository;

import com.uniremington.api.convenia.model.entity.AcademicProgram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcademicProgramRepository extends JpaRepository<AcademicProgram, Long> {

    List<AcademicProgram> findByUniversityIdAndActiveTrue(Long universityId);
}
