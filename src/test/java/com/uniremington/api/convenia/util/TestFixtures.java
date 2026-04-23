package com.uniremington.api.convenia.util;

import com.uniremington.api.convenia.model.dto.AgreementResponse;
import com.uniremington.api.convenia.model.entity.*;
import com.uniremington.api.convenia.model.vo.JwtUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TestFixtures {

    public static University university(Long id) {
        return University.builder()
                .id(id)
                .name("Universidad Test")
                .shortName("UniTest")
                .emailDomain("test.edu.co")
                .address("Calle 1 # 2-3")
                .city("Medellín")
                .country("Colombia")
                .build();
    }

    public static User user(Long id, UserRole role, University university) {
        return User.builder()
                .id(id)
                .email(role.name().toLowerCase() + id + "@test.edu.co")
                .password("encoded_password")
                .fullName("Test " + role.name() + " " + id)
                .role(role)
                .university(university)
                .build();
    }

    public static Student student(Long id, User user, University university) {
        var program = AcademicProgram.builder()
                .id(1L)
                .university(university)
                .name("Ingeniería de Sistemas")
                .faculty("Ingeniería")
                .durationSemesters(10)
                .totalCredits(160)
                .build();

        return Student.builder()
                .id(id)
                .user(user)
                .university(university)
                .academicProgram(program)
                .fullName("Estudiante Test")
                .documentNumber("1234567890")
                .phoneNumber("3001234567")
                .currentSemester(8)
                .approvedCredits(0)
                .seminarsCompleted(0)
                .build();
    }

    public static Company company(Long id, University university) {
        return Company.builder()
                .id(id)
                .university(university)
                .legalName("Empresa Test S.A.S")
                .nit("900123456-7")
                .representativeName("Representante Legal")
                .representativeEmail("rep@empresa.com")
                .build();
    }

    public static Agreement activeAgreement(University university, Student student,
                                            Company company, User advisor, User tutor) {
        return Agreement.builder()
                .id(1L)
                .university(university)
                .student(student)
                .company(company)
                .academicAdvisor(advisor)
                .companyRep(tutor)
                .practiceModality(PracticeModality.PROFESSIONAL)
                .practiceComponent(PracticeComponent.ACADEMIC)
                .contractType(ContractType.EMPLOYMENT)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(6))
                .weeklyHours(40)
                .monthlyStipend(new BigDecimal("1160000.00"))
                .status(AgreementStatus.ACTIVE)
                .build();
    }

    public static JwtUser jwtUser(Long id, String role, Long universityId) {
        return new JwtUser(
                role.toLowerCase() + id + "@test.edu.co",
                id,
                role,
                universityId,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    public static AgreementResponse dummyAgreementResponse() {
        return new AgreementResponse(
                1L, 1L, 1L, "Estudiante Test",
                1L, "Empresa Test S.A.S",
                10L, "academic_advisor10@test.edu.co",
                20L, "company_tutor20@test.edu.co",
                "PROFESSIONAL", "ACADEMIC", "EMPLOYMENT",
                LocalDate.now(), LocalDate.now().plusMonths(6),
                40, new BigDecimal("1160000"),
                "EVALUATION", null, null, null,   // status, rejectionReason, documensoDocumentId, pdfCloudUrl
                null, null, null,                  // advisorGrade, companyGrade, finalGrade
                null, null, null, null, null, null, // cvFileKey..workPlanFileKey
                null, null, null,                  // nitFileKey, rutFileKey, camaraComercioFileKey
                LocalDateTime.now(), LocalDateTime.now()
        );
    }
}
