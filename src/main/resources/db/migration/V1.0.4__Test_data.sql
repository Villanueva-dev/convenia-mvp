-- =============================================================================
-- V1.0.4 — Test data for end-to-end Documenso integration testing
-- Creates: ACADEMIC_ADVISOR, COMPANY_REP, STUDENT users + company + student.
-- Gmail + aliases so all signing invitations arrive at the same inbox.
-- =============================================================================

-- ── Users ─────────────────────────────────────────────────────────────────────

INSERT INTO users (email, password, role, university_id, active, created_at, updated_at) VALUES
    ('julianvilla07021+advisor@gmail.com',
     crypt('Test1234!', gen_salt('bf', 10)),
     'ACADEMIC_ADVISOR', 1, true, now(), now()),

    ('julianvilla07021+rep@gmail.com',
     crypt('Test1234!', gen_salt('bf', 10)),
     'COMPANY_REP', 1, true, now(), now()),

    ('julianvilla07021+student@gmail.com',
     crypt('Test1234!', gen_salt('bf', 10)),
     'STUDENT', 1, true, now(), now());

-- ── Company ───────────────────────────────────────────────────────────────────

INSERT INTO companies (university_id, legal_name, nit, representative_name, representative_email, created_at, updated_at) VALUES
    (1, 'TechCorp Colombia S.A.S.', '900123456-7',
     'Julian Villa', 'julianvilla07021+rep@gmail.com',
     now(), now());

-- ── Student ───────────────────────────────────────────────────────────────────
-- approved_credits=160, total_credits=0 (default) → eligibility check skipped.
-- seminars_completed=2 → passes the seminar requirement.

INSERT INTO students (user_id, university_id, academic_program_id,
                      full_name, document_number, phone_number, current_semester,
                      approved_credits, seminars_completed,
                      created_at, updated_at)
VALUES (
    (SELECT id FROM users WHERE email = 'julianvilla07021+student@gmail.com'),
    1,
    1,
    'Julian Villa (Estudiante Test)',
    '1234567890',
    '3001234567',
    9,
    160,
    2,
    now(),
    now()
);
