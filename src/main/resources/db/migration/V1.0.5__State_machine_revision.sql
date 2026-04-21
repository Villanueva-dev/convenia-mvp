-- =============================================================================
-- V1.0.5 — State machine revision + company document fields
--
-- Changes:
--   1. Replaces COMPLETED state with EVALUATION + FINISHED to model the full
--      practice lifecycle: ACTIVE → EVALUATION → FINISHED.
--   2. Adds company legal document columns (NIT, RUT, Cámara de Comercio)
--      to the agreements table so they are attached to the agreement, not
--      to the company entity.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Update the status CHECK constraint
--    The inline constraint created in V1.0.0 gets the system name
--    agreements_status_check in PostgreSQL.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE agreements DROP CONSTRAINT IF EXISTS agreements_status_check;

ALTER TABLE agreements
    ADD CONSTRAINT agreements_status_check
        CHECK (status IN (
            'DRAFT',
            'ADMIN_REVIEW',
            'COORDINATION_REVIEW',
            'PENDING_SIGNATURE',
            'ACTIVE',
            'EVALUATION',
            'FINISHED',
            'REJECTED'
        ));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Add company legal document columns
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE agreements ADD COLUMN nit_file_key            VARCHAR(500);
ALTER TABLE agreements ADD COLUMN rut_file_key            VARCHAR(500);
ALTER TABLE agreements ADD COLUMN camara_comercio_file_key VARCHAR(500);
