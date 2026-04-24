-- =============================================================================
-- Demo seed — agreements en distintos estados para auditoría
-- =============================================================================
-- Crea 4 agreements adicionales (DRAFT / COORDINATION_REVIEW / ACTIVE / FINISHED)
-- usando los usuarios y la company que crea V1.0.4__Test_data.sql.
--
-- Pre-requisitos:
--   - V1.0.0..V1.1.1 aplicadas.
--   - V1.0.4 corrió (existen advisor/rep/student y la company).
--   - admin@convenia.app existe (V1.0.1 / V1.0.2).
--
-- Ejecutar UNA SOLA VEZ. Re-correrlo crea duplicados (no idempotente).
--
-- Cómo correrlo:
--   psql "<connection-string>" -f tools/seed-demo-agreements.sql
--   o pegarlo en DBeaver / pgAdmin / IntelliJ DB.
-- =============================================================================

BEGIN;

DO $$
DECLARE
    v_uni_id            BIGINT;
    v_admin_id          BIGINT;
    v_advisor_id        BIGINT;
    v_company_rep_id    BIGINT;
    v_student_user_id   BIGINT;
    v_student_id        BIGINT;
    v_company_id        BIGINT;
    v_today             DATE := CURRENT_DATE;

    v_id_draft     BIGINT;
    v_id_coord     BIGINT;
    v_id_active    BIGINT;
    v_id_finished  BIGINT;
BEGIN
    -- ── Resolver IDs por email (no asumir IDs hardcoded) ─────────────────────
    SELECT id INTO v_uni_id FROM universities ORDER BY id LIMIT 1;
    IF v_uni_id IS NULL THEN
        RAISE EXCEPTION 'No universities found. Aplique las migraciones primero.';
    END IF;

    SELECT id INTO v_admin_id        FROM users WHERE email = 'admin@convenia.app';
    SELECT id INTO v_advisor_id      FROM users WHERE email = 'julianvilla07021+advisor@gmail.com';
    SELECT id INTO v_company_rep_id  FROM users WHERE email = 'julianvilla07021+rep@gmail.com';
    SELECT id INTO v_student_user_id FROM users WHERE email = 'julianvilla07021+student@gmail.com';

    IF v_admin_id IS NULL OR v_advisor_id IS NULL
       OR v_company_rep_id IS NULL OR v_student_user_id IS NULL THEN
        RAISE EXCEPTION 'Faltan usuarios del seed V1.0.1/V1.0.2/V1.0.4. Aborto.';
    END IF;

    SELECT id INTO v_student_id FROM students WHERE user_id = v_student_user_id;
    SELECT id INTO v_company_id FROM companies WHERE university_id = v_uni_id ORDER BY id LIMIT 1;

    IF v_student_id IS NULL OR v_company_id IS NULL THEN
        RAISE EXCEPTION 'Falta student/company del seed V1.0.4. Aborto.';
    END IF;

    -- ── Agreement A: DRAFT (estudiante editando, todavía no submit) ───────────
    INSERT INTO agreements (
        university_id, student_id, company_id, academic_advisor_id, company_rep_id,
        practice_modality, practice_component, contract_type,
        start_date, end_date, weekly_hours, monthly_stipend,
        status, created_at, updated_at
    ) VALUES (
        v_uni_id, v_student_id, v_company_id, v_advisor_id, v_company_rep_id,
        'PROFESSIONAL', 'ACADEMIC', 'EMPLOYMENT',
        v_today + INTERVAL '15 days',
        v_today + INTERVAL '15 days' + INTERVAL '6 months',
        40, 1500000.00,
        'DRAFT', now(), now()
    ) RETURNING id INTO v_id_draft;

    -- ── Agreement B: COORDINATION_REVIEW (esperando endorse) ─────────────────
    INSERT INTO agreements (
        university_id, student_id, company_id, academic_advisor_id, company_rep_id,
        practice_modality, practice_component, contract_type,
        start_date, end_date, weekly_hours, monthly_stipend,
        status, created_at, updated_at
    ) VALUES (
        v_uni_id, v_student_id, v_company_id, v_advisor_id, v_company_rep_id,
        'SOCIAL', 'SOCIAL', 'INTERNSHIP_AGREEMENT',
        v_today + INTERVAL '7 days',
        v_today + INTERVAL '7 days' + INTERVAL '5 months',
        30, 1200000.00,
        'COORDINATION_REVIEW', now(), now()
    ) RETURNING id INTO v_id_coord;

    -- ── Agreement C: ACTIVE con 3 visitas (asesor en seguimiento) ─────────────
    INSERT INTO agreements (
        university_id, student_id, company_id, academic_advisor_id, company_rep_id,
        practice_modality, practice_component, contract_type,
        start_date, end_date, weekly_hours, monthly_stipend,
        status,
        documenso_document_id,
        created_at, updated_at
    ) VALUES (
        v_uni_id, v_student_id, v_company_id, v_advisor_id, v_company_rep_id,
        'PROFESSIONAL', 'ACADEMIC', 'APPRENTICESHIP',
        v_today - INTERVAL '2 months',
        v_today + INTERVAL '4 months',
        40, 1800000.00,
        'ACTIVE',
        'demo-envelope-active-001',                  -- ID ficticio, no se llamará a Documenso
        now() - INTERVAL '2 months', now()
    ) RETURNING id INTO v_id_active;

    -- 3 visitas requeridas por la Resolución 002-2024
    INSERT INTO practice_visits
        (agreement_id, advisor_id, visit_date, visit_type, observations, created_at, updated_at)
    VALUES
        (v_id_active, v_advisor_id, v_today - INTERVAL '6 weeks', 'IN_PERSON',
         'Primera visita de seguimiento. Estudiante adaptado al equipo, plan de trabajo en curso. Tutor empresa muy receptivo.',
         now(), now()),
        (v_id_active, v_advisor_id, v_today - INTERVAL '3 weeks', 'VIRTUAL',
         'Segunda visita. Avance del 40% en el plan. Se discuten ajustes de cronograma con el tutor co-formador.',
         now(), now()),
        (v_id_active, v_advisor_id, v_today - INTERVAL '5 days', 'IN_PERSON',
         'Tercera visita de seguimiento. Estudiante completó hito clave, se aprueba apertura de fase de evaluación.',
         now(), now());

    -- ── Agreement D: FINISHED con notas + constancia aprobada ─────────────────
    INSERT INTO agreements (
        university_id, student_id, company_id, academic_advisor_id, company_rep_id,
        practice_modality, practice_component, contract_type,
        start_date, end_date, weekly_hours, monthly_stipend,
        status,
        documenso_document_id,
        advisor_grade, company_grade, final_grade,
        certificate_approved_at, certificate_approved_by_id,
        created_at, updated_at
    ) VALUES (
        v_uni_id, v_student_id, v_company_id, v_advisor_id, v_company_rep_id,
        'PROFESSIONAL', 'MANAGEMENT', 'EMPLOYMENT',
        v_today - INTERVAL '8 months',
        v_today - INTERVAL '2 months',
        40, 1750000.00,
        'FINISHED',
        'demo-envelope-finished-001',
        4.5, 4.0, 4.3,                               -- promedio (4.5+4.0)/2 = 4.25 → redondeado 4.3
        now() - INTERVAL '1 month', v_admin_id,      -- aprobada por admin
        now() - INTERVAL '8 months', now()
    ) RETURNING id INTO v_id_finished;

    -- 3 visitas también para el FINISHED (audit trail completo)
    INSERT INTO practice_visits
        (agreement_id, advisor_id, visit_date, visit_type, observations, created_at, updated_at)
    VALUES
        (v_id_finished, v_advisor_id, v_today - INTERVAL '7 months', 'IN_PERSON',
         'Visita inicial de la práctica. Inducción al área productiva completada.',
         now(), now()),
        (v_id_finished, v_advisor_id, v_today - INTERVAL '5 months', 'VIRTUAL',
         'Visita intermedia. Estudiante demuestra dominio del rol asignado.',
         now(), now()),
        (v_id_finished, v_advisor_id, v_today - INTERVAL '3 months', 'IN_PERSON',
         'Visita final. Plan de trabajo cumplido al 100%. Recomendación positiva del tutor empresa.',
         now(), now());

    RAISE NOTICE 'Demo seed OK — agreements creados: DRAFT=%, COORDINATION_REVIEW=%, ACTIVE=%, FINISHED=%',
        v_id_draft, v_id_coord, v_id_active, v_id_finished;
END $$;

COMMIT;

-- ── Verificación rápida ──────────────────────────────────────────────────────
SELECT id, status, practice_modality, contract_type,
       start_date, end_date,
       advisor_grade, company_grade, final_grade,
       (certificate_approved_at IS NOT NULL) AS cert_approved,
       documenso_document_id
FROM agreements
ORDER BY id;
