# Contexto Maestro: Gestor SaaS de Prácticas (Normativa Resolución 002-2024)

## 1. Estado del Proyecto

**Versión backend:** 0.8.0 — Backend MVP completo + normalización fase 1 (documentos extraídos de `agreements`).
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

- **Backend:** Spring Boot 4 / Java 25 / PostgreSQL / JWT / Flyway / MapStruct
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
| GET | `/api/v1/agreements/{id}/certificate` | Descargar constancia de culminación (FINISHED) |
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

**Diferido post-MVP:** `TIMESTAMPTZ` en las 8 tablas (requiere cambiar `AuditableEntity.LocalDateTime` → `Instant` y añadir `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`).

---

## 7. `User.fullName` (V1.0.7)

Campo `NOT NULL` en `users`, obligatorio para todos los roles. Se alimenta desde:
- `STUDENT` — duplicado desde `students.full_name` (entidad self-contained para PDF)
- `COMPANY_TUTOR` — backfill desde `companies.representative_name` si el email coincide
- `ACADEMIC_ADVISOR`, `ADMIN`, `SECRETARY`, `COORDINATOR` — capturado en el formulario de alta

Se muestra en el PDF del convenio como **Docente Asesor** (cláusula 8ª y firma 1), **Tutor Co-formador** (cláusula 6ª y firma 2) y **Estudiante** (firma 3).

---

## 8. Constancia de culminación (V1.0.8)

Endpoint `GET /api/v1/agreements/{id}/certificate` — emite el PDF oficial cuando el convenio está en `FINISHED`.

- **Storage**: cache-on-first-write en R2 con key `certificates/{universityId}/{agreementId}.pdf`. La segunda llamada sirve desde R2.
- **Template**: `constancia_culminacion.html` (Thymeleaf, gitignored).
- **Contenido**: estudiante (nombre + cédula + programa), empresa (razón social + NIT), fechas, horas semanales + horas totales calculadas, asesor, tutor, notas (50 %/50 %) y nota final.
- **Autorización** (`assertCertificateAccess`):
  - `ADMIN` — cross-tenant.
  - `COORDINATOR` — dentro del mismo `university_id`.
  - `STUDENT` — solo si es el dueño del convenio.
  - `ACADEMIC_ADVISOR` — solo si está asignado al convenio.
  - `COMPANY_TUTOR` — solo si está asignado al convenio.
  - `SECRETARY` y cualquier otro rol → `AccessDeniedException`.

---

## 9. `agreement_documents` (V1.1.0)

Documentos subidos por usuarios (CV, CONTRACT, NATIONAL_ID, EPS, ARL, WORK_PLAN, NIT, RUT, CAMARA_COMERCIO) viven en una tabla normalizada `agreement_documents` con UK `(agreement_id, document_type)` — 1 fila viva por tipo, re-upload hace UPSERT via `ON CONFLICT ON CONSTRAINT uk_agreement_documents DO UPDATE` (atómico, sin TOCTOU). Al re-subir, el archivo anterior en R2 se borra en el mismo flujo; si el borrado falla se loggea como warning (orphan tolerado frente a fallar la request).

Autorización por rol/asignación (`assertDocumentAccess` en el service):

- `ADMIN` — cross-tenant.
- `COORDINATOR` y `SECRETARY` — dentro del mismo `university_id`.
- `STUDENT` — solo dueño del convenio.
- `ACADEMIC_ADVISOR` / `COMPANY_TUTOR` — solo si están asignados al convenio.
- Cualquier otro → `AccessDeniedException`.

Los artefactos generados por el sistema (`documenso_document_id`, `pdf_cloud_url`, `certificate_file_key`) siguen en `agreements` — semántica distinta (sin uploader, 1:1 con el convenio).

`AgreementResponse` ya no incluye los 9 file keys. El frontend consulta `GET /agreements/{id}/documents` para listar metadata (sin fileKey — no se expone el key R2 para evitar enumeración).

## 10. Pendiente backend

| Ítem | Prioridad |
|------|-----------|
| *(nada pendiente para MVP)* | — |

### Deuda técnica documentada (post-MVP)

- **Concurrencia del cache**: dos requests simultáneos de primera vez pueden renderizar + subir el PDF 2 veces. Key determinista → idempotente. TODO en el Javadoc de `downloadCertificate`. Fix: `@Lock(LockModeType.PESSIMISTIC_WRITE)` en el finder.
- **Null `universityId` en tokens no-ADMIN**: `assertTenantAccess` short-circuita, dando acceso cross-tenant. Afecta a TODOS los endpoints del backend. Fix: validación defensiva en `JwtAuthenticationFilter`.
- **Secretos en `application.yml`**: rotar y mover a env-only sin defaults (`APP_JWT_SECRET`, `DOCUMENSO_TOKEN`, `app.storage.access-key/secret-key`).
- **Seeds de prueba en Flyway**: V1.0.1, V1.0.2, V1.0.4, parte de V1.0.7 contienen usuarios/password de test. Separar por perfil (`spring.flyway.locations`) antes de ir a producción.
- **Emails de asesor/tutor en la constancia**: PII presente en el PDF que recibe el estudiante. Decisión de negocio pendiente (¿ocultar por minimización de PII?).
- **Magic numbers dispersos**: 3 visitas, 4/12 meses, 20/48 h/sem, 80/70 % créditos. Extraer a `PracticeConstants` citando la Resolución.

---

## 11. Seed data de pruebas

- Universidad Remington id=1
- `admin@convenia.app` / `Admin1234!` (ADMIN)
- `julianvilla07021+advisor@gmail.com` / `Test1234!` (ACADEMIC_ADVISOR)
- `julianvilla07021+rep@gmail.com` / `Test1234!` (COMPANY_TUTOR)
- `julianvilla07021+student@gmail.com` / `Test1234!` (STUDENT)
