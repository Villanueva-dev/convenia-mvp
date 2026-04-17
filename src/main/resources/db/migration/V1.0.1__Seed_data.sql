-- =============================================================================
-- V1.0.1 — Seed data for demo and development
-- Inserts the minimum data required to run the application and test the MVP.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- Universidad Remington  (the tenant used for development and demo)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO universities (name, short_name, email_domain, address, city, country, active, created_at, updated_at)
VALUES (
    'Corporación Universitaria Remington',
    'UNIREMINGTON',
    'uniremington.edu.co',
    'Calle 51 #51-27',
    'Medellín',
    'Colombia',
    true,
    NOW(),
    NOW()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Admin user
-- email   : admin@convenia.app
-- password: Admin1234!
-- BCrypt hash (strength 10) — generated with BCryptPasswordEncoder
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO users (email, password, role, university_id, active, created_at, updated_at)
VALUES (
    'admin@convenia.app',
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
    'ADMIN',
    NULL,  -- ADMIN is platform-level, not bound to any university
    true,
    NOW(),
    NOW()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Academic programs for Universidad Remington
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO academic_programs (university_id, name, faculty, duration_semesters, active, created_at, updated_at)
VALUES
    (1, 'Ingeniería de Sistemas',     'Facultad de Ingeniería',           10, true, NOW(), NOW()),
    (1, 'Administración de Empresas', 'Facultad de Ciencias Económicas',  10, true, NOW(), NOW()),
    (1, 'Contaduría Pública',         'Facultad de Ciencias Económicas',  10, true, NOW(), NOW()),
    (1, 'Derecho',                    'Facultad de Ciencias Jurídicas',   10, true, NOW(), NOW()),
    (1, 'Psicología',                 'Facultad de Ciencias Sociales',    10, true, NOW(), NOW());
