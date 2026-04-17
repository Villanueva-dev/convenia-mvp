-- =============================================================================
-- V1.0.0 — Initial schema
-- Creates all tables for the Convenia platform.
-- Flyway runs this ONCE on first startup, before Hibernate validates the schema.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- universities  (tenant root — every record belongs to a university)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE universities (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL,
    short_name  VARCHAR(50)     NOT NULL,
    email_domain VARCHAR(100)   NOT NULL UNIQUE,
    address     VARCHAR(255)    NOT NULL,
    city        VARCHAR(100)    NOT NULL,
    country     VARCHAR(100)    NOT NULL DEFAULT 'Colombia',
    logo_url    VARCHAR(500),
    active      BOOLEAN         NOT NULL DEFAULT true,
    created_at  TIMESTAMP       NOT NULL,
    updated_at  TIMESTAMP       NOT NULL
);

-- ─────────────────────────────────────────────────────────────────────────────
-- academic_programs  (belongs to a university)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE academic_programs (
    id                   BIGSERIAL      PRIMARY KEY,
    university_id        BIGINT         NOT NULL,
    name                 VARCHAR(255)   NOT NULL,
    faculty              VARCHAR(255)   NOT NULL,
    duration_semesters   INTEGER        NOT NULL,
    active               BOOLEAN        NOT NULL DEFAULT true,
    created_at           TIMESTAMP      NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,

    CONSTRAINT fk_program_university
        FOREIGN KEY (university_id) REFERENCES universities (id)
);

CREATE INDEX idx_program_university ON academic_programs (university_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- users  (authentication identity — one row per login account)
-- UserRole enum values: ADMIN, COORDINATOR, ACADEMIC_ADVISOR, COMPANY_REP, STUDENT
-- university_id is NULL only for ADMIN users (platform-level).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            BIGSERIAL      PRIMARY KEY,
    email         VARCHAR(255)   NOT NULL UNIQUE,
    password      VARCHAR(255)   NOT NULL,
    role          VARCHAR(50)    NOT NULL
                      CHECK (role IN ('ADMIN', 'COORDINATOR', 'ACADEMIC_ADVISOR', 'COMPANY_REP', 'STUDENT')),
    university_id BIGINT,
    active        BOOLEAN        NOT NULL DEFAULT true,
    created_at    TIMESTAMP      NOT NULL,
    updated_at    TIMESTAMP      NOT NULL,

    CONSTRAINT fk_user_university
        FOREIGN KEY (university_id) REFERENCES universities (id)
);

CREATE INDEX idx_user_email      ON users (email);
CREATE INDEX idx_user_university ON users (university_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- students  (academic profile — 1:1 with users)
-- document_number is unique per university (composite constraint).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE students (
    id                   BIGSERIAL      PRIMARY KEY,
    user_id              BIGINT         NOT NULL UNIQUE,
    university_id        BIGINT         NOT NULL,
    academic_program_id  BIGINT         NOT NULL,
    full_name            VARCHAR(255)   NOT NULL,
    document_number      VARCHAR(20)    NOT NULL,
    phone_number         VARCHAR(20)    NOT NULL,
    address              VARCHAR(500),
    current_semester     INTEGER        NOT NULL,
    created_at           TIMESTAMP      NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,

    CONSTRAINT fk_student_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_student_university
        FOREIGN KEY (university_id) REFERENCES universities (id),
    CONSTRAINT fk_student_program
        FOREIGN KEY (academic_program_id) REFERENCES academic_programs (id),
    CONSTRAINT uk_student_document_university
        UNIQUE (document_number, university_id)
);

CREATE INDEX idx_student_university ON students (university_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- companies  (host company for the internship)
-- nit is unique per university (multi-tenant: same company could be in 2 unis).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE companies (
    id                    BIGSERIAL      PRIMARY KEY,
    university_id         BIGINT         NOT NULL,
    legal_name            VARCHAR(255)   NOT NULL,
    nit                   VARCHAR(30)    NOT NULL,
    representative_name   VARCHAR(255)   NOT NULL,
    representative_email  VARCHAR(255)   NOT NULL,
    created_at            TIMESTAMP      NOT NULL,
    updated_at            TIMESTAMP      NOT NULL,

    CONSTRAINT fk_company_university
        FOREIGN KEY (university_id) REFERENCES universities (id),
    CONSTRAINT uk_company_nit_university
        UNIQUE (nit, university_id)
);

CREATE INDEX idx_company_university ON companies (university_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- agreements  (core domain entity — the practice contract)
-- Links 3 parties: student, university, company.
-- 2 protagonists: academic_advisor (User) + company_rep (User).
-- Enum constraints keep the DB consistent even without Hibernate.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE agreements (
    id                      BIGSERIAL       PRIMARY KEY,

    -- Three parties
    university_id           BIGINT          NOT NULL,
    student_id              BIGINT          NOT NULL,
    company_id              BIGINT          NOT NULL,

    -- Two protagonists
    academic_advisor_id     BIGINT          NOT NULL,
    company_rep_id          BIGINT          NOT NULL,

    -- Configuration
    practice_modality       VARCHAR(50)     NOT NULL
                                CHECK (practice_modality IN ('PROFESSIONAL', 'SOCIAL', 'RESEARCH', 'INTERNATIONAL')),
    contract_type           VARCHAR(50)     NOT NULL
                                CHECK (contract_type IN ('EMPLOYMENT', 'APPRENTICESHIP', 'INTERNSHIP_AGREEMENT', 'FRAMEWORK_AGREEMENT')),
    start_date              DATE            NOT NULL,
    end_date                DATE            NOT NULL,
    weekly_hours            INTEGER         NOT NULL
                                CHECK (weekly_hours >= 20 AND weekly_hours <= 48),
    monthly_stipend         NUMERIC(12, 2)  NOT NULL,

    -- Workflow state
    status                  VARCHAR(50)     NOT NULL DEFAULT 'DRAFT'
                                CHECK (status IN ('DRAFT', 'ADMIN_REVIEW', 'COORDINATION_REVIEW', 'PENDING_SIGNATURE', 'ACTIVE', 'COMPLETED', 'REJECTED')),
    rejection_reason        TEXT,

    -- Document keys (Cloudflare R2 / S3)
    cv_file_key             VARCHAR(500),
    contract_file_key       VARCHAR(500),
    national_id_file_key    VARCHAR(500),
    eps_file_key            VARCHAR(500),
    arl_file_key            VARCHAR(500),
    work_plan_file_key      VARCHAR(500),

    -- Documenso integration
    documenso_document_id   VARCHAR(255),
    pdf_cloud_url           VARCHAR(500),

    -- Evaluation grades (0.0 to 5.0)
    advisor_grade           NUMERIC(3, 1)   CHECK (advisor_grade >= 0.0 AND advisor_grade <= 5.0),
    company_grade           NUMERIC(3, 1)   CHECK (company_grade >= 0.0 AND company_grade <= 5.0),
    final_grade             NUMERIC(3, 1)   CHECK (final_grade  >= 0.0 AND final_grade  <= 5.0),

    -- Audit
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP       NOT NULL,

    -- Foreign keys
    CONSTRAINT fk_agreement_university
        FOREIGN KEY (university_id) REFERENCES universities (id),
    CONSTRAINT fk_agreement_student
        FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_agreement_company
        FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_agreement_advisor
        FOREIGN KEY (academic_advisor_id) REFERENCES users (id),
    CONSTRAINT fk_agreement_company_rep
        FOREIGN KEY (company_rep_id) REFERENCES users (id),

    -- Business rule: end_date must be after start_date
    CONSTRAINT chk_agreement_dates
        CHECK (end_date > start_date)
);

CREATE INDEX idx_agreement_university ON agreements (university_id);
CREATE INDEX idx_agreement_student    ON agreements (student_id);
CREATE INDEX idx_agreement_status     ON agreements (status);
