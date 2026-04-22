-- =============================================================================
-- V1.0.6 — Schema hardening
--
-- HIGH: Missing indexes on agreements (company_id, advisor_id, company_rep_id,
--       documenso_document_id) + composite coordinator-dashboard index.
-- HIGH: Missing FK indexes on students and composite on users.
-- HIGH: CHECK constraints for multi-tenancy and business rules.
-- MEDIUM: Composite indexes on practice_visits and agreement_status_history.
-- CLEANUP: Drop redundant idx_user_email (UNIQUE already creates an implicit index).
--
-- Note: CONCURRENTLY is omitted — Flyway runs migrations in a transaction by
-- default and CONCURRENTLY is incompatible with open transactions. For a dev /
-- MVP environment the lock is negligible. Use CONCURRENTLY manually in prod
-- if the tables are large enough to warrant it.
-- =============================================================================


-- ── agreements: missing FK indexes ───────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_agreement_company
    ON agreements (company_id);

CREATE INDEX IF NOT EXISTS idx_agreement_advisor
    ON agreements (academic_advisor_id);

CREATE INDEX IF NOT EXISTS idx_agreement_company_rep
    ON agreements (company_rep_id);

-- Partial index for the Documenso webhook hot path: findByDocumensoDocumentId.
-- Skips NULL rows, keeping the index small.
CREATE INDEX IF NOT EXISTS idx_agreement_documenso_id
    ON agreements (documenso_document_id)
    WHERE documenso_document_id IS NOT NULL;


-- ── agreements: composite indexes for coordinator/student dashboards ──────────

-- findByUniversityIdAndStatusOrderByCreatedAtDesc — coordinator filtered view
CREATE INDEX IF NOT EXISTS idx_agreement_university_status_created
    ON agreements (university_id, status, created_at DESC);

-- findByUniversityIdOrderByCreatedAtDesc — default listing
CREATE INDEX IF NOT EXISTS idx_agreement_university_created
    ON agreements (university_id, created_at DESC);

-- findByStudentIdOrderByCreatedAtDesc — student's own agreements
CREATE INDEX IF NOT EXISTS idx_agreement_student_created
    ON agreements (student_id, created_at DESC);


-- ── students: missing FK index ────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_student_program
    ON students (academic_program_id);


-- ── users: composite for role-filtered dropdown queries ──────────────────────
-- findByUniversityIdAndRoleOrderByEmailAsc (advisor / tutor selection dropdowns)

CREATE INDEX IF NOT EXISTS idx_user_university_role
    ON users (university_id, role);


-- ── practice_visits: composite covers findBy + countBy + sort ────────────────
-- findByAgreementIdOrderByVisitDateAsc + countByAgreementId

CREATE INDEX IF NOT EXISTS idx_visit_agreement_date
    ON practice_visits (agreement_id, visit_date ASC);


-- ── agreement_status_history: composite with sort key ────────────────────────
-- findByAgreementIdOrderByCreatedAtAsc

CREATE INDEX IF NOT EXISTS idx_history_agreement_created
    ON agreement_status_history (agreement_id, created_at ASC);


-- ── Drop redundant idx_user_email ─────────────────────────────────────────────
-- users.email has a UNIQUE constraint; PostgreSQL automatically creates a unique
-- index for it. The explicit idx_user_email duplicates that index and adds write
-- overhead on every user INSERT/UPDATE with no read benefit.

DROP INDEX IF EXISTS idx_user_email;


-- ── CHECK: non-ADMIN users must belong to a university ───────────────────────
-- Prevents multi-tenancy bypass where a non-admin user slips through with
-- university_id = NULL. NOT VALID checks only new rows immediately;
-- run VALIDATE CONSTRAINT during a low-traffic window to backfill existing rows.

ALTER TABLE users
    ADD CONSTRAINT chk_non_admin_has_university
        CHECK (role = 'ADMIN' OR university_id IS NOT NULL)
        NOT VALID;


-- ── CHECK: REJECTED agreements must carry rejection_reason ───────────────────
-- Resolución CF No. 002 de 2024 requires a documented justification for every
-- rejection. The API layer validates this, but the DB constraint is the safety net.

ALTER TABLE agreements
    ADD CONSTRAINT chk_rejected_has_reason
        CHECK (status != 'REJECTED' OR rejection_reason IS NOT NULL)
        NOT VALID;


-- ── CHECK: final_grade requires both source grades to be non-NULL ─────────────
-- Prevents inconsistent evaluation state where a final grade exists without
-- both the advisor and company grades being recorded first.

ALTER TABLE agreements
    ADD CONSTRAINT chk_final_grade_requires_sources
        CHECK (
            final_grade IS NULL
            OR (advisor_grade IS NOT NULL AND company_grade IS NOT NULL)
        )
        NOT VALID;
