-- =============================================================================
-- V1.0.8 — Add certificate_file_key to agreements
--
-- Caches the R2 key of the generated "Constancia de Culminación" PDF so the
-- certificate is only rendered once per FINISHED agreement. Nullable because
-- the column stays empty until the first GET /agreements/{id}/certificate
-- request for a FINISHED agreement.
-- =============================================================================

ALTER TABLE agreements ADD COLUMN certificate_file_key VARCHAR(255);
