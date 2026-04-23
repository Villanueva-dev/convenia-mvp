-- =============================================================================
-- V1.0.7 — Validate V1.0.6 constraints + add full_name to users
--
-- Part 1: Validate the three NOT VALID constraints added in V1.0.6 against
--         existing rows. VALIDATE takes a ShareUpdateExclusiveLock (does not
--         block reads or writes). Safe to run on a populated DB.
--
-- Part 2: Add `full_name` column to `users` with multi-stage backfill, then
--         enforce NOT NULL. This closes the gap that made advisors and
--         company tutors appear in the agreement PDF by email only.
-- =============================================================================


-- ── Part 1: Validate existing constraints ────────────────────────────────────

ALTER TABLE users       VALIDATE CONSTRAINT chk_non_admin_has_university;
ALTER TABLE agreements  VALIDATE CONSTRAINT chk_rejected_has_reason;
ALTER TABLE agreements  VALIDATE CONSTRAINT chk_final_grade_requires_sources;


-- ── Part 2: Add users.full_name + backfill + NOT NULL ────────────────────────

-- Step 1: add as nullable so existing rows don't fail
ALTER TABLE users ADD COLUMN full_name VARCHAR(255);

-- Step 2a: students have the authoritative full name on students.full_name
UPDATE users u
   SET full_name = s.full_name
  FROM students s
 WHERE s.user_id = u.id;

-- Step 2b: company tutors whose email matches a company.representative_email
--         get the company's legal representative name
UPDATE users u
   SET full_name = c.representative_name
  FROM companies c
 WHERE u.full_name IS NULL
   AND u.role = 'COMPANY_TUTOR'
   AND c.representative_email = u.email;

-- Step 2c: explicit seed data (admin + advisor test)
UPDATE users SET full_name = 'Administrador Convenia'
 WHERE email = 'admin@convenia.app'
   AND full_name IS NULL;

UPDATE users SET full_name = 'Julián Villa (Asesor Test)'
 WHERE email = 'julianvilla07021+advisor@gmail.com'
   AND full_name IS NULL;

-- Step 2d: generic fallback for any row still unresolved — email local part
UPDATE users SET full_name = split_part(email, '@', 1)
 WHERE full_name IS NULL;

-- Step 3: enforce NOT NULL
ALTER TABLE users ALTER COLUMN full_name SET NOT NULL;
