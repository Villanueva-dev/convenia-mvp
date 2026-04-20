-- =============================================================================
-- V1.0.2 — Fix admin password hash
-- Uses PostgreSQL's pgcrypto extension to generate a valid BCrypt hash
-- that Spring Security's BCryptPasswordEncoder can verify.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE users
SET    password = crypt('Admin1234!', gen_salt('bf', 10))
WHERE  email = 'admin@convenia.app';
