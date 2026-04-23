-- V1.1.0 — Normalize documents out of `agreements`
--
-- The `agreements` table carried 9 `*_file_key` columns (CV, CONTRACT,
-- NATIONAL_ID, EPS, ARL, WORK_PLAN from the student; NIT, RUT, CAMARA_COMERCIO
-- from the company). This violated 1NF and forced a switch-based mapping in
-- the service layer. This migration extracts them to a normalized
-- `agreement_documents` table.
--
-- System artefacts (documenso_document_id, pdf_cloud_url, certificate_file_key)
-- are NOT touched — they have a different semantics (1:1 with the agreement,
-- no uploader, deterministic key) and will be considered separately.
--
-- PostgreSQL DDL is transactional, so this migration is atomic: if the backfill
-- fails, the DROP COLUMN reverts and the new table disappears.

CREATE TABLE agreement_documents (
    id              BIGSERIAL    PRIMARY KEY,
    agreement_id    BIGINT       NOT NULL REFERENCES agreements(id) ON DELETE CASCADE,
    document_type   VARCHAR(32)  NOT NULL,
    file_key        VARCHAR(500) NOT NULL,
    content_type    VARCHAR(100),
    original_name   VARCHAR(255),
    file_size_bytes BIGINT,
    uploaded_by_id  BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT chk_document_type CHECK (document_type IN (
        'CV','CONTRACT','NATIONAL_ID','EPS','ARL','WORK_PLAN',
        'NIT','RUT','CAMARA_COMERCIO'
    )),
    CONSTRAINT uk_agreement_documents UNIQUE (agreement_id, document_type)
);

-- The UK already indexes `(agreement_id, document_type)`, whose leftmost prefix
-- covers "all docs for this agreement". Only the uploader FK needs its own index.
CREATE INDEX idx_agreement_documents_uploaded_by_id ON agreement_documents(uploaded_by_id);

-- Backfill: student documents (uploader = student.user_id)
INSERT INTO agreement_documents (
    agreement_id, document_type, file_key, uploaded_by_id, created_at, updated_at
)
SELECT a.id, 'CV', a.cv_file_key, s.user_id, a.created_at, a.updated_at
  FROM agreements a JOIN students s ON s.id = a.student_id
 WHERE a.cv_file_key IS NOT NULL
UNION ALL
SELECT a.id, 'CONTRACT', a.contract_file_key, s.user_id, a.created_at, a.updated_at
  FROM agreements a JOIN students s ON s.id = a.student_id
 WHERE a.contract_file_key IS NOT NULL
UNION ALL
SELECT a.id, 'NATIONAL_ID', a.national_id_file_key, s.user_id, a.created_at, a.updated_at
  FROM agreements a JOIN students s ON s.id = a.student_id
 WHERE a.national_id_file_key IS NOT NULL
UNION ALL
SELECT a.id, 'EPS', a.eps_file_key, s.user_id, a.created_at, a.updated_at
  FROM agreements a JOIN students s ON s.id = a.student_id
 WHERE a.eps_file_key IS NOT NULL
UNION ALL
SELECT a.id, 'ARL', a.arl_file_key, s.user_id, a.created_at, a.updated_at
  FROM agreements a JOIN students s ON s.id = a.student_id
 WHERE a.arl_file_key IS NOT NULL
UNION ALL
SELECT a.id, 'WORK_PLAN', a.work_plan_file_key, s.user_id, a.created_at, a.updated_at
  FROM agreements a JOIN students s ON s.id = a.student_id
 WHERE a.work_plan_file_key IS NOT NULL
UNION ALL
-- Company documents (uploader = agreements.company_rep_id)
SELECT a.id, 'NIT', a.nit_file_key, a.company_rep_id, a.created_at, a.updated_at
  FROM agreements a
 WHERE a.nit_file_key IS NOT NULL
UNION ALL
SELECT a.id, 'RUT', a.rut_file_key, a.company_rep_id, a.created_at, a.updated_at
  FROM agreements a
 WHERE a.rut_file_key IS NOT NULL
UNION ALL
SELECT a.id, 'CAMARA_COMERCIO', a.camara_comercio_file_key, a.company_rep_id, a.created_at, a.updated_at
  FROM agreements a
 WHERE a.camara_comercio_file_key IS NOT NULL;

-- Drop the 9 denormalized columns from agreements
ALTER TABLE agreements
    DROP COLUMN cv_file_key,
    DROP COLUMN contract_file_key,
    DROP COLUMN national_id_file_key,
    DROP COLUMN eps_file_key,
    DROP COLUMN arl_file_key,
    DROP COLUMN work_plan_file_key,
    DROP COLUMN nit_file_key,
    DROP COLUMN rut_file_key,
    DROP COLUMN camara_comercio_file_key;
