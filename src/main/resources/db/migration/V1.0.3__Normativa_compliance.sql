-- =============================================================================
-- V1.0.3 — Normativa compliance (Resolución 002-2024)
-- Adds eligibility fields to academic_programs and students,
-- creates practice_visits and agreement_status_history tables.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- academic_programs: program type (PROFESSIONAL / TECHNOLOGICAL) + total credits
-- These two fields determine the eligibility threshold (80% vs 70%).
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE academic_programs
    ADD COLUMN program_type   VARCHAR(50)  NOT NULL DEFAULT 'PROFESSIONAL'
        CHECK (program_type IN ('PROFESSIONAL', 'TECHNOLOGICAL')),
    ADD COLUMN total_credits  INTEGER      NOT NULL DEFAULT 0;

-- ─────────────────────────────────────────────────────────────────────────────
-- students: approved credits + mandatory seminars completed
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE students
    ADD COLUMN approved_credits    INTEGER  NOT NULL DEFAULT 0,
    ADD COLUMN seminars_completed  INTEGER  NOT NULL DEFAULT 0
        CHECK (seminars_completed >= 0 AND seminars_completed <= 2);

-- ─────────────────────────────────────────────────────────────────────────────
-- practice_visits  (follow-up visits by the academic advisor)
-- Minimum 3 visits required per active agreement (Resolución 002-2024, Art. §6).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE practice_visits (
    id            BIGSERIAL     PRIMARY KEY,
    agreement_id  BIGINT        NOT NULL,
    advisor_id    BIGINT        NOT NULL,
    visit_date    DATE          NOT NULL,
    visit_type    VARCHAR(50)   NOT NULL
                      CHECK (visit_type IN ('IN_PERSON', 'VIRTUAL')),
    observations  TEXT          NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,

    CONSTRAINT fk_visit_agreement
        FOREIGN KEY (agreement_id) REFERENCES agreements (id),
    CONSTRAINT fk_visit_advisor
        FOREIGN KEY (advisor_id) REFERENCES users (id)
);

CREATE INDEX idx_visit_agreement ON practice_visits (agreement_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- agreement_status_history  (append-only audit trail)
-- One row per status transition. Records who changed the status and when.
-- changed_by_id is NULL for system-triggered changes (e.g., Documenso webhook).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE agreement_status_history (
    id             BIGSERIAL    PRIMARY KEY,
    agreement_id   BIGINT       NOT NULL,
    from_status    VARCHAR(50),
    to_status      VARCHAR(50)  NOT NULL,
    changed_by_id  BIGINT,
    notes          TEXT,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,

    CONSTRAINT fk_history_agreement
        FOREIGN KEY (agreement_id) REFERENCES agreements (id),
    CONSTRAINT fk_history_user
        FOREIGN KEY (changed_by_id) REFERENCES users (id)
);

CREATE INDEX idx_history_agreement ON agreement_status_history (agreement_id);
