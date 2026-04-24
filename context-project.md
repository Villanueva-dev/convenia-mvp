# Contexto Maestro: Gestor SaaS de Prácticas (Normativa Resolución 002-2024)

## 1. Estado del Proyecto

**Versión backend:** 0.10.0 — MVP completo + preparación para demo (deploy-ready): multipart 5 MB con handler 413, endpoint `GET /universities` para selectores, Java downgradeado a 21 para runtime estable.
**Frontend:** Angular 21, en desarrollo (`convenia-web/`).
**Prioridad actual:** Terminar el frontend para demo completa del flujo MVP + hardening pre-producción (secretos y separación de seeds por perfil).

---

## 2. Dominio y Reglas de Negocio

### Tipos y Modalidades (Enums)
- **Modalidad:** `PROFESSIONAL`, `SOCIAL`, `RESEARCH`, `INTERNATIONAL`
- **Tipo de vinculación:** `EMPLOYMENT`, `APPRENTICESHIP`, `INTERNSHIP_AGREEMENT`, `FRAMEWORK_AGREEMENT`

### Validaciones normativas (Resolución 002-2024)
- **Créditos mínimos:** 80% aprobados (Profesional) / 70% (Tecnológico)
- **Seminarios:** mínimo 2 seminarios de práctica completados antes de enviar
- **Duración:** mínimo 4 meses (20h/sem), máximo 12 meses (48h/sem). APPRENTICESHIP = exactamente 6 meses
- **Visitas:** mínimo 3 visitas del asesor antes de abrir evaluación
- **Calificación:** 50% Asesor académico + 50% Tutor empresa (escala 0.0–5.0)

### Roles
`ADMIN`, `SECRETARY`, `COORDINATOR`, `ACADEMIC_ADVISOR`, `COMPANY_TUTOR`, `STUDENT`

---

## 3. Arquitectura Técnica

- **Backend:** Spring Boot 4 / **Java 21** / PostgreSQL / JWT / Flyway / MapStruct
- **Frontend:** Angular 21 (standalone, OnPush, signals, reactive forms, Angular Material)
- **Almacenamiento:** Cloudflare R2 (S3-compatible) — upload + download implementados
- **Firmas:** Documenso v2 REST API (template mode + direct PDF-upload mode)
- **Multi-tenancy:** `university_id` en todas las tablas; ADMIN tiene `university_id = NULL`

---

## 4. Flujo de Estados del Convenio

```
DRAFT → ADMIN_REVIEW → COORDINATION_REVIEW → PENDING_SIGNATURE → ACTIVE → EVALUATION → FINISHED
             ↓                    ↓
         (vuelve DRAFT)       REJECTED (terminal)
```

| Estado | Qué ocurre | Actor principal |
|--------|-----------|-----------------|
| DRAFT | Estudiante crea, sube CV. Empresa sube NIT/RUT/Cámara. | STUDENT, COMPANY_TUTOR |
| ADMIN_REVIEW | Secretaría valida docs. Puede devolver a DRAFT con razón. | SECRETARY |
| COORDINATION_REVIEW | Coordinación audita y avala. Genera PDF + envía a Documenso. | COORDINATOR |
| PENDING_SIGNATURE | Estudiante sube docs de afiliación (ARL, EPS, etc.). 3 partes firman. | STUDENT + Documenso |
| ACTIVE | Webhook activa. Asesor registra mínimo 3 visitas. | ACADEMIC_ADVISOR |
| EVALUATION | Coordinador abre fase. Asesor y tutor califican (50%/50%). | ACADEMIC_ADVISOR, COMPANY_TUTOR |
| FINISHED | Nota final calculada automáticamente. Flujo cerrado. | Sistema |

---

## 5. Endpoints principales del backend

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/auth/login` | Login → JWT |
| POST | `/auth/register/{role}` | Registro público (student/advisor/company-tutor) |
| GET | `/api/v1/agreements` | Lista convenios del usuario |
| POST | `/api/v1/agreements` | Crear convenio (DRAFT) |
| GET | `/api/v1/agreements/{id}` | Detalle del convenio |
| POST | `/api/v1/agreements/{id}/submit` | DRAFT → ADMIN_REVIEW |
| POST | `/api/v1/agreements/{id}/approve` | ADMIN_REVIEW → COORDINATION_REVIEW |
| POST | `/api/v1/agreements/{id}/reject` | → DRAFT (admin) o REJECTED (coord) |
| POST | `/api/v1/agreements/{id}/endorse` | COORDINATION_REVIEW → PENDING_SIGNATURE |
| POST | `/api/v1/agreements/{id}/start-evaluation` | ACTIVE → EVALUATION (requiere 3 visitas) |
| PUT | `/api/v1/agreements/{id}/grade` | Calificar (EVALUATION) |
| POST | `/api/v1/agreements/{id}/documents/{type}` | Subir documento a R2 (UPSERT, borra archivo viejo si había) |
| GET | `/api/v1/agreements/{id}/documents` | Listar metadata de documentos subidos |
| GET | `/api/v1/agreements/{id}/documents/{type}` | Descargar documento de R2 |
| GET | `/api/v1/agreements/{id}/document` | Descargar PDF firmado de Documenso |
| POST | `/api/v1/agreements/{id}/approve-certificate` | Aprobar constancia (solo COORDINATOR/ADMIN; requiere FINISHED) |
| GET | `/api/v1/agreements/{id}/certificate` | Descargar constancia de culminación (requiere FINISHED + aprobación) |
| GET | `/api/v1/universities` | Listar universidades (cualquier autenticado, para selectores ADMIN) |
| GET | `/api/v1/agreements/{id}/visits` | Listar visitas |
| POST | `/api/v1/agreements/{id}/visits` | Registrar visita (ACTIVE) |
| POST | `/api/v1/webhooks/signature` | Webhook Documenso → activa convenio |

---

## 6. Migraciones Flyway

| Versión | Descripción |
|---------|-------------|
| V1.0.0 | Esquema inicial |
| V1.0.1 | Seed data |
| V1.0.2 | Fix password admin |
| V1.0.3 | Normativa compliance |
| V1.0.4 | Test data |
| V1.0.5 | Revisión máquina de estados |
| V1.0.6 | Schema hardening: 10 índices nuevos (partial en `agreements(documenso_document_id)`, composites para dashboards), drop `idx_user_email`, 3 CHECK constraints NOT VALID |
| V1.0.7 | `VALIDATE CONSTRAINT` de V1.0.6 + `users.full_name NOT NULL` con backfill en 4 etapas |
| V1.0.8 | `agreements.certificate_file_key VARCHAR(255) NULL` — cache de R2 de la constancia |
| V1.1.0 | Normalización fase 1: nueva tabla `agreement_documents` con UK `(agreement_id, document_type)`; backfill desde los 9 `*_file_key` de `agreements`; DROP de las 9 columnas. |
| V1.1.1 | `agreements.certificate_approved_at TIMESTAMP NULL` + `agreements.certificate_approved_by_id BIGINT FK(users)` + CHECK `chk_certificate_approval_pair` (ambos NULL o ambos populados). |

**Diferido post-MVP:** `TIMESTAMPTZ` en las 8 tablas (requiere cambiar `AuditableEntity.LocalDateTime` → `Instant` y añadir `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`).

---

## 7. `User.fullName` (V1.0.7)

Campo `NOT NULL` en `users`, obligatorio para todos los roles. Se alimenta desde:
- `STUDENT` — duplicado desde `students.full_name` (entidad self-contained para PDF)
- `COMPANY_TUTOR` — backfill desde `companies.representative_name` si el email coincide
- `ACADEMIC_ADVISOR`, `ADMIN`, `SECRETARY`, `COORDINATOR` — capturado en el formulario de alta

Se muestra en el PDF del convenio como **Docente Asesor** (cláusula 8ª y firma 1), **Tutor Co-formador** (cláusula 6ª y firma 2) y **Estudiante** (firma 3).

---

## 8. Constancia de culminación (V1.0.8 + V1.1.1)

Dos endpoints — la emisión requiere **aprobación explícita** por parte de la coordinación antes de que cualquier participante pueda descargar.

- `POST /api/v1/agreements/{id}/approve-certificate` — solo `COORDINATOR` o `ADMIN`; agreement debe estar en `FINISHED`; si ya fue aprobada responde 409. Setea `certificateApprovedAt = now()` y `certificateApprovedBy = user`.
- `GET /api/v1/agreements/{id}/certificate` — emite/sirve el PDF. Devuelve 409 mientras `certificateApprovedAt` sea null.

Storage y contenido del PDF sin cambios:
- Cache-on-first-write en R2: `certificates/{universityId}/{agreementId}.pdf`.
- Template `constancia_culminacion.html` (Thymeleaf, gitignored).
- Contenido: estudiante (nombre + cédula + programa), empresa (razón social + NIT), fechas, horas semanales + horas totales, asesor, tutor, notas (50%/50%) y nota final.

**Autorización de descarga** (`assertCertificateAccess`):
- `ADMIN` — cross-tenant.
- `COORDINATOR` — dentro del mismo `university_id`.
- `STUDENT` — solo si es el dueño del convenio.
- `ACADEMIC_ADVISOR` / `COMPANY_TUTOR` — solo si están asignados.
- `SECRETARY` y cualquier otro rol → `AccessDeniedException`.

**Invariant DB** (V1.1.1): CHECK `chk_certificate_approval_pair` garantiza que `certificate_approved_at` y `certificate_approved_by_id` son ambos NULL o ambos populados.

---

## 9a. Acceso a agreements (v0.9.0 — IDOR fix)

`listAgreements` y `getAgreement` fueron endurecidos — antes un `ACADEMIC_ADVISOR` o `COMPANY_TUTOR` veía TODOS los convenios de su tenant. Ahora:

- `listAgreements` por rol: STUDENT ve los suyos; ACADEMIC_ADVISOR ve donde está asignado; COMPANY_TUTOR ve donde está asignado; COORDINATOR y SECRETARY ven todo el tenant; ADMIN ve todo.
- `getAgreement` usa nuevo helper `assertAgreementReadAccess` con las mismas reglas a nivel de detalle (no más IDOR a recursos individuales del mismo tenant).

Nuevos queries en `AgreementRepository`:
- `findByAcademicAdvisorIdOrderByCreatedAtDesc`
- `findByCompanyRepIdOrderByCreatedAtDesc`

## 9b. Alta de COORDINATOR (v0.9.0)

`AllowedRole` ahora incluye `COORDINATOR`, pero el service aplica jerarquía: **solo ADMIN puede crear COORDINATOR**; un COORDINATOR no puede crear otro COORDINATOR (evita escalada horizontal de privilegios). COORDINATOR sigue pudiendo crear ACADEMIC_ADVISOR / COMPANY_TUTOR / SECRETARY en su tenant.

Endpoint complementario: **`GET /api/v1/universities`** (v0.10.0) para que el ADMIN pueda seleccionar la universidad del nuevo usuario desde un dropdown. Cualquier autenticado puede listarlas.

## 9d. Límite de uploads (v0.10.0)

- `application.yml`: `spring.servlet.multipart.max-file-size: 5MB` + `max-request-size: 5MB`. Alineado con la validación local del frontend.
- `GlobalExceptionHandler` maneja `MaxUploadSizeExceededException` → **HTTP 413 Payload Too Large** con RFC 7807 y mensaje parametrizado con el límite configurado.

## 9c. `agreement_documents` (V1.1.0)

Documentos subidos por usuarios (CV, CONTRACT, NATIONAL_ID, EPS, ARL, WORK_PLAN, NIT, RUT, CAMARA_COMERCIO) viven en una tabla normalizada `agreement_documents` con UK `(agreement_id, document_type)` — 1 fila viva por tipo, re-upload hace UPSERT via `ON CONFLICT ON CONSTRAINT uk_agreement_documents DO UPDATE` (atómico, sin TOCTOU). Al re-subir, el archivo anterior en R2 se borra en el mismo flujo; si el borrado falla se loggea como warning (orphan tolerado frente a fallar la request).

Autorización por rol/asignación (`assertDocumentAccess` en el service):

- `ADMIN` — cross-tenant.
- `COORDINATOR` y `SECRETARY` — dentro del mismo `university_id`.
- `STUDENT` — solo dueño del convenio.
- `ACADEMIC_ADVISOR` / `COMPANY_TUTOR` — solo si están asignados al convenio.
- Cualquier otro → `AccessDeniedException`.

Los artefactos generados por el sistema (`documenso_document_id`, `pdf_cloud_url`, `certificate_file_key`) siguen en `agreements` — semántica distinta (sin uploader, 1:1 con el convenio).

`AgreementResponse` ya no incluye los 9 file keys. El frontend consulta `GET /agreements/{id}/documents` para listar metadata (sin fileKey — no se expone el key R2 para evitar enumeración).

## 10. Preparación para despliegue de demo (v0.10.0)

Backend está **deploy-ready**. Checklist de lo que está listo vs. lo que debe hacerse antes de exponerse a usuarios reales:

### ✅ Listo para demo

- Java 21 (runtime LTS, amplio soporte en PaaS: Railway, Render, Fly.io, Heroku-like).
- Spring Boot 4.0.5 con `spring.config.import=optional:file:.env` (env-first, sin defaults hardcoded de secretos).
- Multipart con límite 5 MB.
- Flyway aplica las 9 migraciones al arranque (`V1.0.0 → V1.0.8 + V1.1.0 + V1.1.1`).
- JWT con fail-fast si `APP_JWT_SECRET` no está seteado.
- 80 tests pasando.

### ⚠️ Pendientes antes de producción real (bloquean solo "prod", no la demo)

1. **Rotar credenciales** expuestas en el histórico público:
   - R2 access/secret keys (generar par nuevo en Cloudflare + actualizar `.env`).
   - Documenso API token (regenerar desde el dashboard).
   - JWT secret (cualquier `openssl rand -base64 48`).
2. **Separar seed de test data** en Flyway — V1.0.1, V1.0.2, V1.0.4, parte de V1.0.7 contienen usuarios/passwords de prueba. Requiere `spring.flyway.locations` por perfil (`dev` vs `prod`).
3. **Bajar nivel de logging** en prod: `application.yml` tiene `org.hibernate.SQL: DEBUG` y `orm.jdbc.bind: TRACE` — cambiar a `INFO`/`WARN` para producción.
4. **Desactivar Swagger UI en prod**: setear `springdoc.swagger-ui.enabled=false` y `springdoc.api-docs.enabled=false` cuando el perfil sea `prod`.
5. **CORS origins**: `APP_ALLOWED_ORIGINS` debe configurarse con el dominio real del frontend en producción (no `localhost`).
6. **`spring.jpa.show-sql: true`** — deshabilitar en prod, solo útil en dev/test.
7. **Deuda diferida** (no bloqueantes para demo): `TIMESTAMPTZ` en las 10 tablas, `@Lock` para concurrencia del cache de constancia, bypass de tenant si `universityId` null en JWT no-ADMIN, magic numbers dispersos, limpieza periódica de objetos R2 huérfanos.

### Variables de entorno obligatorias en prod

`APP_JWT_SECRET`, `APP_STORAGE_ENDPOINT_URL`, `APP_STORAGE_ACCESS_KEY`, `APP_STORAGE_SECRET_KEY`, `DOCUMENSO_TOKEN`, `DB_HOST/PORT/NAME/USER/PASSWORD`, `APP_ALLOWED_ORIGINS`, `DOCUMENSO_WEBHOOK_SECRET`.

## 11. Pendiente backend

| Ítem | Prioridad |
|------|-----------|
| *(nada pendiente para el MVP de demo)* | — |

### Deuda técnica documentada (post-MVP)

- **Concurrencia del cache**: dos requests simultáneos de primera vez pueden renderizar + subir el PDF 2 veces. Key determinista → idempotente. TODO en el Javadoc de `downloadCertificate`. Fix: `@Lock(LockModeType.PESSIMISTIC_WRITE)` en el finder.
- **Null `universityId` en tokens no-ADMIN**: `assertTenantAccess` short-circuita, dando acceso cross-tenant. Afecta a TODOS los endpoints del backend. Fix: validación defensiva en `JwtAuthenticationFilter`.
- **Secretos en `application.yml`**: rotar y mover a env-only sin defaults (`APP_JWT_SECRET`, `DOCUMENSO_TOKEN`, `app.storage.access-key/secret-key`).
- **Seeds de prueba en Flyway**: V1.0.1, V1.0.2, V1.0.4, parte de V1.0.7 contienen usuarios/password de test. Separar por perfil (`spring.flyway.locations`) antes de ir a producción.
- **Emails de asesor/tutor en la constancia**: PII presente en el PDF que recibe el estudiante. Decisión de negocio pendiente (¿ocultar por minimización de PII?).
- **Magic numbers dispersos**: 3 visitas, 4/12 meses, 20/48 h/sem, 80/70 % créditos. Extraer a `PracticeConstants` citando la Resolución.

---

## 12. Seed data de pruebas

- Universidad Remington id=1
- `admin@convenia.app` / `Admin1234!` (ADMIN)
- `julianvilla07021+advisor@gmail.com` / `Test1234!` (ACADEMIC_ADVISOR)
- `julianvilla07021+rep@gmail.com` / `Test1234!` (COMPANY_TUTOR)
- `julianvilla07021+student@gmail.com` / `Test1234!` (STUDENT)
