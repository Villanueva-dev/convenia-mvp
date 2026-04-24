-- V1.1.1 — Certificate approval by coordination
--
-- Adds a manual approval step to the constancia de culminación: once the
-- agreement is FINISHED, a COORDINATOR (or ADMIN) must approve the certificate
-- before it can be downloaded by any role. Until approved, GET /certificate
-- returns 409 Conflict.

ALTER TABLE agreements
    ADD COLUMN certificate_approved_at   TIMESTAMP,
    ADD COLUMN certificate_approved_by_id BIGINT REFERENCES users(id) ON DELETE RESTRICT;

-- Invariant: either both NULL (not approved yet) or both populated.
ALTER TABLE agreements
    ADD CONSTRAINT chk_certificate_approval_pair
    CHECK (
        (certificate_approved_at IS NULL AND certificate_approved_by_id IS NULL)
        OR
        (certificate_approved_at IS NOT NULL AND certificate_approved_by_id IS NOT NULL)
    );
