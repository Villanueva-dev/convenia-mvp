# Convenia API — Changelog

## [0.10.0] — 2026-04-24

### Contexto
Preparación para el despliegue de la demo del MVP. Tres cambios funcionales (multipart 5 MB + handler 413, directorio de universidades para selectores) y un ajuste de runtime (downgrade a Java 21 LTS para compatibilidad con PaaS).

---

### Límite de multipart uploads

**Por qué:** el límite por defecto de Spring Boot es **1 MB**, pero el frontend valida hasta 5 MB localmente. Un PDF escaneado de CV o contrato casi siempre pasa del MB, así que el upload fallaba con `MaxUploadSizeExceededException` mapeado a 500 genérico — error confuso para el usuario.

**Cambios:**
- `application.yml`: bloque nuevo
  ```yaml
  spring:
    servlet:
      multipart:
        max-file-size:    5MB
        max-request-size: 5MB
  ```
- `GlobalExceptionHandler`: nuevo `@ExceptionHandler(MaxUploadSizeExceededException.class)` → **HTTP 413 Payload Too Large** con RFC 7807. El mensaje se parametriza con `ex.getMaxUploadSize()` así que si mañana se cambia el límite, el texto lo refleja automáticamente: *"El archivo supera el tamaño máximo permitido (5 MB). Comprime el PDF o reduce su resolución e intenta de nuevo."*

**Defense in depth:** el frontend valida primero (`onFileSelected`, 5 MB) y el backend también. Mismo número en ambos lados; si se pasa modificando la request directamente, el backend rechaza con mensaje claro.

---

### `GET /api/v1/universities` — directorio de universidades

**Por qué:** `UserServiceImpl` permite que `ADMIN` cree usuarios en cualquier tenant especificando `universityId` en el request. El frontend no tenía forma de descubrir qué universidades existen, así que el form de alta de usuarios fallaba cuando el ADMIN intentaba crear una secretaría (`auth.universityId()` es `null` para ADMIN).

**Cambios:**
- `model/dto/UniversitySummaryResponse.java` — DTO compacto `{id, name, shortName, city}` con factory `from(University)`.
- `controller/UniversityController.java` — `GET /api/v1/universities`, `@SecurityRequirement(name = "bearerAuth")`. Sin `@PreAuthorize` específico: cualquier autenticado puede listar. Ordenado alfabéticamente por nombre.

**Consumidor frontend:** `UserService` del frontend ahora precarga universidades cuando el rol es `ADMIN` y las muestra como dropdown. Si hay una sola, queda preseleccionada. Detalle en el CHANGELOG del frontend.

---

### Java 21 (downgrade desde Java 25)

**Por qué:** Java 25 salió hace poco y las PaaS más comunes para demos (Railway, Render, Fly.io, Heroku-like) no lo ofrecen como runtime soportado todavía. Java 21 LTS tiene cobertura amplia y todas las features que usamos funcionan igual (records, switch-expressions, pattern matching, virtual threads, sealed types).

**Cambios:**
- `pom.xml`: `<java.version>21</java.version>`.
- Sin cambios en código — nada del código usaba features exclusivas de 25.
- 80/80 tests siguen pasando en Java 21.

**Lecciones:** mantener la versión de Java siempre en LTS (17, 21, 25 cuando tenga 2+ años) para evitar fricción en deploys.

---

### Cambios de código

**Creados:**
- `src/main/java/com/uniremington/api/convenia/model/dto/UniversitySummaryResponse.java`
- `src/main/java/com/uniremington/api/convenia/controller/UniversityController.java`

**Modificados:**
- `src/main/resources/application.yml` — bloque `spring.servlet.multipart`.
- `src/main/java/com/uniremington/api/convenia/shared/exception/GlobalExceptionHandler.java` — `@ExceptionHandler(MaxUploadSizeExceededException.class)` + import.
- `pom.xml` — `java.version` 25 → 21.

### Tests

**80 → 80** (sin nuevos tests en esta versión; Java 21 los ejecuta limpios).

---

### Preparación para demo — checklist

Backend está deploy-ready. Pendientes antes de **producción real** (no bloquean la demo):

- Rotar credenciales expuestas en histórico git (R2, Documenso, JWT).
- Separar seeds de test data en Flyway por perfil.
- Bajar logging level (`SQL: DEBUG` → `INFO`, `jdbc.bind: TRACE` → `WARN`).
- Deshabilitar Swagger UI en perfil `prod`.
- `APP_ALLOWED_ORIGINS` con dominio real (no localhost).
- Deshabilitar `spring.jpa.show-sql`.

Ver detalle completo en `context-project.md §10`.

---

## [0.9.0] — 2026-04-23

### Contexto
Cierre del MVP end-to-end: bug de seguridad en el listado/detalle de convenios (IDOR entre asesores/tutores del mismo tenant), paso de aprobación formal de la coordinación sobre la constancia de culminación, y habilitación del alta de `COORDINATOR` vía el endpoint de usuarios (que hasta ahora solo dejaba crear roles operativos).

---

### IDOR en agreements — fix (crítico)

**Por qué:** `AgreementServiceImpl.listAgreements` caía en un `default` case que retornaba `findByUniversityIdOrderByCreatedAtDesc(...)` para cualquier rol no STUDENT/ADMIN. Efecto: un `ACADEMIC_ADVISOR` del tenant veía (y podía `GET /{id}` sobre) convenios asignados a **otros asesores**. Mismo problema con `COMPANY_TUTOR`. Detectado en testing manual por el usuario.

**Cambios:**
- `AgreementRepository`: nuevos finders tipados
  - `findByAcademicAdvisorIdOrderByCreatedAtDesc(Long)`
  - `findByCompanyRepIdOrderByCreatedAtDesc(Long)`
- `AgreementServiceImpl.listAgreements`: `switch` con casos explícitos para `ACADEMIC_ADVISOR` y `COMPANY_TUTOR` (filtro por asignación); `COORDINATOR` y `SECRETARY` siguen viendo todo el tenant (requisito de rol).
- `getAgreement`: nuevo helper `assertAgreementReadAccess` — mismo patrón que `assertDocumentAccess` y `assertCertificateAccess`. ADMIN cross-tenant; COORDINATOR/SECRETARY tenant-local; STUDENT/ADVISOR/TUTOR solo su asignación.

**Tests:** +6 en la nested class `TenantIsolation` y nueva `ListAgreementsByRole` (advisor/tutor no asignado → 403; list filtrado por rol; coordinator unrestricted within tenant).

---

### Aprobación de constancia de culminación (V1.1.1)

**Por qué:** antes cualquier participante con acceso al agreement FINISHED podía descargar la constancia inmediatamente. No había un paso de validación institucional. Regla de negocio: la coordinación debe dar el visto bueno antes de que el documento sea "oficial".

**Schema (V1.1.1):**
```sql
ALTER TABLE agreements
    ADD COLUMN certificate_approved_at    TIMESTAMP,
    ADD COLUMN certificate_approved_by_id BIGINT REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE agreements ADD CONSTRAINT chk_certificate_approval_pair
    CHECK ((certificate_approved_at IS NULL AND certificate_approved_by_id IS NULL)
        OR (certificate_approved_at IS NOT NULL AND certificate_approved_by_id IS NOT NULL));
```
El CHECK es defensa en profundidad: el schema mismo expresa el invariant. Imposible a nivel DB tener "aprobada sin aprobador".

**API:**
- `POST /api/v1/agreements/{id}/approve-certificate` — `@PreAuthorize('COORDINATOR','ADMIN')`. Valida `status == FINISHED`, tenant, y que no esté ya aprobada (409).
- `GET /api/v1/agreements/{id}/certificate` — ahora también verifica `certificateApprovedAt != null`; si falta, lanza `IllegalStateException` → 409.

**DTO:** `AgreementResponse` expone `certificateApprovedAt` y `certificateApprovedBy` (solo el userId). Mapper actualizado.

**Tests:** +5 — nested class `ApproveCertificate` (happy, not FINISHED, already approved, cross-tenant) y `DownloadCertificate.deniesDownloadUntilCertificateApproved`.

---

### Alta de COORDINATOR vía endpoint de usuarios

**Por qué:** el enum `AllowedRole` solo tenía `ACADEMIC_ADVISOR`, `COMPANY_TUTOR`, `SECRETARY`. Era imposible crear un nuevo `COORDINATOR` desde el API — el único que había era el del seed V1.0.4 (si existe; en la base actual de dev no hay). Detectado cuando el usuario intentó darse de alta como coordinador y vio 500 por deserialización.

**Cambios:**
- `AllowedRole`: añadido `COORDINATOR`.
- `UserServiceImpl.createManagedUser`: nueva regla — si `request.role() == COORDINATOR` y el llamador **no es ADMIN**, lanza `AccessDeniedException("Only ADMIN can create COORDINATOR accounts")`. Previene escalada horizontal de privilegios.
- Switch `toUserRole` extendido con el caso `COORDINATOR`.

**Tests:** +2 — `adminCanCreateCoordinator` (happy) y `coordinatorCannotCreateAnotherCoordinator` (denied). El test parametrizado `@EnumSource` se acotó a los tres roles operativos para que sus aserciones sigan siendo válidas.

---

### Cambios de código

**Creados:**
- `src/main/resources/db/migration/V1.1.1__Add_certificate_approval.sql`.

**Modificados:**
- `model/entity/Agreement.java` — 2 campos nuevos (`certificateApprovedAt`, `certificateApprovedBy` `@ManyToOne`).
- `model/dto/AgreementResponse.java` + `model/mapper/AgreementMapper.java` — proyección del aprobador.
- `model/dto/AllowedRole.java` — +COORDINATOR.
- `service/AgreementService.java` / `service/impl/AgreementServiceImpl.java` — `approveCertificate`, `assertAgreementReadAccess`, check de aprobación en download, scoping por rol en list.
- `service/impl/UserServiceImpl.java` — jerarquía ADMIN-only para COORDINATOR.
- `repository/AgreementRepository.java` — 2 finders.
- `controller/AgreementController.java` — endpoint `POST /{id}/approve-certificate`.
- `util/TestFixtures.java` — `dummyAgreementResponse` ajustado al nuevo shape.

---

### Tests

**73 → 80** (+7 nuevos, 0 fallos).

- `TenantIsolation` (+3): advisor no asignado denegado, tutor no asignado denegado, coordinator mismo-tenant OK.
- `ListAgreementsByRole` (+3): advisor filtrado por asignación, tutor filtrado, coordinator ve todo el tenant.
- `DownloadCertificate.deniesDownloadUntilCertificateApproved` (+1).
- `ApproveCertificate` (+4): happy, wrong status, already approved, cross-tenant.
- `UserServiceImplTest` (+2): admin crea coordinator, coordinator denegado para crear otro coordinator.

---

## [0.8.0] — 2026-04-23

### Contexto
Primer paso de normalización de la base de datos. La tabla `agreements` acumulaba 9 columnas `*_file_key` dispersas que violaban 1NF y forzaban un switch duplicado en el service. Esta versión las extrae a una tabla normalizada y endurece la autorización de documentos.

---

### Nueva tabla `agreement_documents` (V1.1.0)

**Por qué:** 9 columnas de file_key en `agreements` (CV, CONTRACT, NATIONAL_ID, EPS, ARL, WORK_PLAN, NIT, RUT, CAMARA_COMERCIO) son atributos repetidos — 1NF. Cada upload obligaba a `save(agreement)` sobre una entidad con 38 columnas, y el mapeo `DocumentType → campo` estaba duplicado entre `uploadDocument` (líneas 433-443) y el helper `fileKeyFor` (650-662).

**Schema (validado con `database-reviewer`):**

```sql
CREATE TABLE agreement_documents (
    id              BIGSERIAL    PRIMARY KEY,
    agreement_id    BIGINT       NOT NULL REFERENCES agreements(id) ON DELETE CASCADE,
    document_type   VARCHAR(32)  NOT NULL,
    file_key        VARCHAR(500) NOT NULL,
    content_type    VARCHAR(100),
    original_name   VARCHAR(255),
    file_size_bytes BIGINT,
    uploaded_by_id  BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT chk_document_type CHECK (document_type IN ('CV','CONTRACT','NATIONAL_ID','EPS','ARL','WORK_PLAN','NIT','RUT','CAMARA_COMERCIO')),
    CONSTRAINT uk_agreement_documents UNIQUE (agreement_id, document_type)
);
CREATE INDEX idx_agreement_documents_uploaded_by_id ON agreement_documents(uploaded_by_id);
```

- `UNIQUE (agreement_id, document_type)` — una fila viva por tipo; re-upload hace UPSERT.
- `chk_document_type` — CHECK consistente con el patrón del proyecto (mismo estilo que `status`, `role`, `practice_modality`).
- No se creó `idx_agreement_documents_agreement_id`: el UK ya lo cubre como leftmost prefix.
- No `DEFAULT CURRENT_TIMESTAMP` en timestamps: coherente con el resto del schema (JPA puebla vía `@CreatedDate`/`@LastModifiedDate`).

**Migración atómica:** la V1.1.0 hace `CREATE + INSERT ... SELECT (UNION ALL)` copiando desde las 9 columnas (student docs → `student.user_id`, company docs → `agreements.company_rep_id`) + `DROP COLUMN` de las 9 columnas de `agreements`. PostgreSQL DDL es transaccional: si algo falla, revierte todo.

---

### Upsert atómico `ON CONFLICT DO UPDATE`

**Por qué:** el flujo anterior era find-then-set-then-save: leía el field, lo sobreescribía, y guardaba. Con dos requests concurrentes del mismo tipo había una ventana TOCTOU donde ambos podían "ganar". Ahora el patrón es una única sentencia atómica a nivel DB.

```sql
INSERT INTO agreement_documents (...) VALUES (...)
ON CONFLICT ON CONSTRAINT uk_agreement_documents
DO UPDATE SET file_key        = EXCLUDED.file_key,
              content_type    = EXCLUDED.content_type,
              original_name   = EXCLUDED.original_name,
              file_size_bytes = EXCLUDED.file_size_bytes,
              uploaded_by_id  = EXCLUDED.uploaded_by_id,
              updated_at      = CURRENT_TIMESTAMP
```

Implementado como `@Modifying @Query(nativeQuery=true)` en `AgreementDocumentRepository.upsert(...)`. Elimina `DataIntegrityViolationException` visible al usuario en colisiones.

---

### Autorización endurecida de documentos

**Por qué:** el `downloadDocument` anterior solo llamaba `assertTenantAccess` — cualquier usuario del mismo tenant podía descargar documentos de cualquier agreement (IDOR). Además, `listDocuments` nuevo no tenía `@PreAuthorize` y habría expuesto metadata sin restricciones por rol. Detectados por `security-reviewer`.

**Nuevo helper `assertDocumentAccess(Agreement, JwtUser)`:**

| Rol | Regla |
|-----|-------|
| `ADMIN` | Cross-tenant, pasa |
| `COORDINATOR`, `SECRETARY` | Tenant-local |
| `STUDENT` | Solo si es dueño del agreement |
| `ACADEMIC_ADVISOR` | Solo si está asignado al agreement |
| `COMPANY_TUTOR` | Solo si está asignado al agreement |
| Resto | `AccessDeniedException` |

Aplicado a `listDocuments` **y** `downloadDocument`. En `uploadDocument` la lógica previa (por rol + estado + ownership) se mantuvo.

**`@PreAuthorize` añadido** en los 2 endpoints `GET /{id}/documents` y `GET /{id}/documents/{type}` que antes no lo tenían.

**`fileKey` removido del `AgreementDocumentResponse`:** exponerlo permitía enumerar keys R2 (patrón `agreements/{id}/{type}/{uuid}.{ext}` es deterministico). El frontend descarga bytes por el endpoint tipado, nunca usa el key directo.

---

### Nuevo endpoint `GET /agreements/{id}/documents`

Lista metadata de todos los documentos subidos (sin `fileKey`). El frontend lo consulta para saber qué tipos ya están arriba sin necesidad de leerlos todos uno por uno.

**Response (`AgreementDocumentResponse`):**
```json
{
  "id": 7,
  "documentType": "CV",
  "contentType": "application/pdf",
  "originalName": "cv.pdf",
  "fileSizeBytes": 1024,
  "uploadedById": 30,
  "uploadedAt": "2026-04-23T15:31:39"
}
```

---

### Re-upload borra el archivo R2 anterior

**Por qué:** antes, al re-subir un doc (p. ej. CV rechazado), el `file_key` anterior se sobrescribía en la BD y el objeto R2 viejo quedaba huérfano creciendo el bucket indefinidamente.

**Ahora:** `uploadDocument` lee el `previousKey` antes del upload → sube el archivo nuevo → upsert en DB → `storageService.delete(previousKey)`. Si el delete falla, se loggea como warning y no se propaga (orphan tolerable; fallar la request después de persistir el nuevo doc sería peor).

**`StorageService.delete(String)`** — nuevo método, implementado en `R2StorageServiceImpl` con `s3Client.deleteObject(DeleteObjectRequest)`.

---

### AgreementResponse más limpio

Los 9 campos `*FileKey` fueron retirados del DTO. La fuente de verdad de "¿qué documentos hay?" es el endpoint `GET /documents` (metadata) o `GET /documents/{type}` (bytes).

---

### Cambios de código

**Creados:**
- `model/entity/AgreementDocument.java` — entidad JPA que extiende `AuditableEntity`.
- `model/entity/DocumentType.java` — movido desde `model/dto/`; ahora es valor persistido.
- `repository/AgreementDocumentRepository.java` — CRUD + `findByAgreementIdAndDocumentType` + `findAllByAgreementId` + `upsert` nativo.
- `model/dto/AgreementDocumentResponse.java` — DTO sin `fileKey`.
- `db/migration/V1.1.0__Extract_agreement_documents.sql`.

**Modificados:**
- `model/entity/Agreement.java` — eliminados los 9 `*FileKey`.
- `model/dto/AgreementResponse.java` — eliminados los 9 `*FileKey` del record.
- `service/AgreementService.java` — nuevo `listDocuments`; `downloadDocument` ahora queries.
- `service/impl/AgreementServiceImpl.java` — reescrito `uploadDocument` (upsert + delete R2 previo); reescrito `downloadDocument`; nuevo `listDocuments`; nuevo helper `assertDocumentAccess`; eliminado helper `fileKeyFor`.
- `service/StorageService.java` + `service/impl/R2StorageServiceImpl.java` — nuevo `delete(String)`.
- `controller/AgreementController.java` — nuevo endpoint `GET /documents`; `@PreAuthorize` añadido a listar + descargar.
- `util/TestFixtures.java` — ajuste de `dummyAgreementResponse` (ya no tiene los 9 nulls).

---

### Tests

**50 → 67** (+17). 0 fallos.

- `UploadDocument` (8): happy path, re-upload con delete R2, fallo de delete R2 no-bloqueante, fallo R2 antes del upsert (verifica no-orphan-DB), student bloqueado para NIT, tutor no asignado denegado, estado incorrecto denegado, cross-tenant denegado.
- `DownloadDocumentTests` (5): happy path, 404 si no subido, cross-tenant denegado, **student no-owner denegado (regression para el IDOR que reportó security-reviewer)**, tutor no asignado denegado.
- `ListDocumentsTests` (4): owner student, coordinator del mismo tenant, tutor no asignado denegado, cross-tenant denegado.

---

### Revisores automáticos

- `database-reviewer` durante el plan: sugirió retirar índice redundante + usar `ON CONFLICT` para upsert.
- `java-reviewer` post-implementación: detectó tradeoff pre-existente de `@Transactional` con upload R2 (aceptado como deuda); sugirió test de R2-fail antes del upsert (añadido).
- `security-reviewer` post-implementación: detectó **3 majors** — IDOR en `downloadDocument`, `listDocuments` sin `@PreAuthorize`, `fileKey` expuesto en response. Los 3 corregidos antes de cerrar.

---

## [0.7.0] — 2026-04-22

### Contexto
Tres bloques de trabajo intercalados: cierre del último pendiente MVP (constancia de culminación para FINISHED), consolidación de `full_name` como campo obligatorio en `users`, y limpieza de credenciales hardcoded en el repo público.

---

### Constancia de culminación (V1.0.8)

**Por qué:** el flujo cerraba en FINISHED sin emitir un documento oficial. La Resolución CF 002-2024 exige una constancia con datos del estudiante, empresa, fechas, horas, notas (asesor 50% + tutor 50%) y nota final.

**Nuevo endpoint:**
```
GET /api/v1/agreements/{id}/certificate
```

- Solo para agreements en `FINISHED`; 409 en otros estados.
- **Cache-on-first-write en R2**: key determinista `certificates/{universityId}/{agreementId}.pdf`. Primera llamada genera + sube; las siguientes sirven desde R2.
- Nuevo campo `agreements.certificate_file_key VARCHAR(255) NULL` (migración V1.0.8).

**Autorización (`assertCertificateAccess`):**
- `ADMIN` — cross-tenant.
- `COORDINATOR` — tenant-local.
- `STUDENT` — solo dueño del convenio.
- `ACADEMIC_ADVISOR` / `COMPANY_TUTOR` — solo si están asignados.
- `SECRETARY` y resto — denegado.

**Template:** `src/main/resources/templates/constancia_culminacion.html` (Thymeleaf; gitignored como el convenio).

**Tests:** 11 nuevos en `@Nested DownloadCertificate` — happy path con `ArgumentCaptor`, cache hit, not-FINISHED, upload-failure sin persistir file_key, coordinator-same-tenant, 5 denegaciones por rol/tenant, ADMIN bypass.

---

### `users.full_name NOT NULL` (V1.0.6 + V1.0.7)

**Por qué:** el PDF del convenio necesitaba mostrar los nombres reales del Docente Asesor y del Tutor Co-formador. Antes, `users.full_name` era opcional y se poblaba solo para estudiantes (desde `students.full_name`), dejando al asesor/tutor con email como "nombre".

**V1.0.6 — schema hardening**:
- 10 índices nuevos (incluye parcial en `agreements(documenso_document_id) WHERE IS NOT NULL` y composites para dashboards de coordinador).
- Drop de `idx_user_email` redundante (ya había UK).
- 3 CHECK constraints `NOT VALID`: `chk_non_admin_has_university`, `chk_rejected_has_reason`, `chk_final_grade_requires_sources`.

**V1.0.7 — consolidación `full_name`**:
- `VALIDATE CONSTRAINT` de los 3 CHECKs de V1.0.6.
- `ALTER TABLE users ADD COLUMN full_name VARCHAR NOT NULL` con **backfill en 4 etapas**:
  1. STUDENT ← `students.full_name`.
  2. COMPANY_TUTOR ← `companies.representative_name` si el email coincide.
  3. ADMIN / ACADEMIC_ADVISOR seed ← valores explícitos.
  4. Fallback ← prefijo del email.

**`RegisterRequest` y `CreateUserRequest`** — ahora exigen `@NotBlank fullName` para todos los roles.

**`PdfGenerationServiceImpl`** — publica `advisorName` y `companyTutorName` como variables de contexto. Firmas del PDF del convenio pasan de Coordinación/RepLegal/Student a Advisor/Tutor/Student (los 3 que realmente firman en Documenso).

---

### Hardening de credenciales

**Por qué:** el repo es **público** y `application.yml` + `pom.xml` tenían credenciales hardcoded desde el commit `5642420 MVP`: R2 access/secret keys (literales sin `${ENV}`), Documenso token default, JWT secret default, credenciales Flyway (`postgres:postgres`) en properties.

**Cambios:**

- **`application.yml`**: R2 keys, JWT secret y Documenso token migrados a `${ENV_VAR}` **sin default** → fail-fast si falta la env var. Se añade `spring.config.import: "optional:file:.env[.properties]"` para que Spring Boot cargue `.env` automáticamente en local (feature nativa, sin librería extra).
- **`pom.xml`**: eliminadas las properties `flyway.url/user/password`. Spring aplica las migraciones al arrancar; el plugin Maven sigue funcionando si se pasan las credenciales con `-Dflyway.url=... -Dflyway.user=... -Dflyway.password=...`.
- **`.env`** (gitignored): expandido a 6+ variables (prefijo `APP_STORAGE_` para relaxed-binding con `app.storage.*`); JWT secret aleatorio regenerado con `openssl rand -base64 48`; espacios tras `=` corregidos (rompían el parser `.properties`).
- **`.env.example`**: nuevo, checkeado a git, con placeholders y comentarios.
- **`.gitignore`**: añadidos `.env.local`, `.env.*.local`, `!.env.example`.
- **`README.md`**: sección "Variables de entorno" reescrita con tabla de obligatorias vs. opcionales + paso `cp .env.example .env` en el flujo de arranque + nota de que las keys del histórico git **deben rotarse** antes de producción.

**Riesgo aceptado:** las credenciales expuestas en commits pasados no se eliminaron del histórico en esta versión (requiere `git filter-repo` + force-push). El usuario reservó la rotación para cuando el compañero encargado esté disponible.

---

### Pendientes para el cierre MVP

| # | Ítem | Estado |
|---|------|--------|
| 1 | Constancia de culminación | ✅ Cerrado en esta versión |
| 2 | Rotación de credenciales del repo público | Pospuesto (riesgo aceptado) |

---

## [0.6.0] — 2026-04-22

### Contexto
Cierre de los dos pendientes medios identificados en v0.5.0: visibilidad de documentos para los revisores y corrección temprana de asignaciones de rol incorrectas. Con esta versión el flujo de revisión documental queda completo de extremo a extremo.

---

### Endpoint de descarga de documentos R2

**Por qué:** La secretaría y la coordinación no podían ver los archivos subidos durante las fases ADMIN_REVIEW y COORDINATION_REVIEW. Los file keys existían en la base de datos pero no había forma de obtener los bytes desde el frontend.

**Nuevo endpoint:**
```
GET /api/v1/agreements/{id}/documents/{type}
```
Devuelve el archivo en `application/pdf` con `Content-Disposition: attachment`. Soporta todos los tipos: `CV`, `NIT`, `RUT`, `CAMARA_COMERCIO`, `CONTRACT`, `NATIONAL_ID`, `EPS`, `ARL`, `WORK_PLAN`.

- Si el documento no ha sido subido aún → `404 Not Found` con mensaje descriptivo.
- Control de acceso: cualquier usuario autenticado con acceso al tenant puede descargar (secretaría, coordinación, asesor, tutor, estudiante).

**`StorageService`** — nuevo método `download(String key) → byte[]`

**`R2StorageServiceImpl`** — implementación con `s3Client.getObjectAsBytes(GetObjectRequest)`

**`AgreementServiceImpl`** — `downloadDocument()` + helper privado `fileKeyFor(agreement, type)` que mapea `DocumentType` → campo del entity.

**`AgreementController`** — endpoint `GET /{id}/documents/{type}`

**Archivos modificados:**
- `service/StorageService.java`
- `service/AgreementService.java`
- `service/impl/R2StorageServiceImpl.java`
- `service/impl/AgreementServiceImpl.java`
- `controller/AgreementController.java`

---

### Validación de rol al asignar asesor/tutor

**Por qué:** `loadUser()` en `createAgreement` y `updateAgreement` cargaba cualquier usuario sin verificar su rol. Se podía asignar un estudiante como asesor académico sin error; el fallo ocurría mucho más tarde en `gradeAgreement`, haciendo muy difícil el diagnóstico.

**Fix:** Nuevo overload `loadUser(Long id, UserRole expectedRole)`. Si el rol no coincide → `IllegalArgumentException` → **400 Bad Request** inmediato al crear o actualizar el acuerdo.

- `academicAdvisorId` → valida `UserRole.ACADEMIC_ADVISOR`
- `companyRepId` → valida `UserRole.COMPANY_TUTOR`

**Archivos modificados:**
- `service/impl/AgreementServiceImpl.java`

**Total: 39 tests, 0 fallos.**

---

### Pendientes para revisión antes de cierre MVP

| # | Ítem | Estado |
|---|------|--------|
| 1 | Constancia de culminación — `GET /agreements/{id}/certificate` para FINISHED | Aplazado |

---

## [0.5.0] — 2026-04-22

### Contexto
Dos sprints de calidad sobre el backend: cobertura completa de errores HTTP y corrección de bugs críticos detectados en el análisis de gaps del MVP. El objetivo es que cada falla tenga un código de estado y un mensaje precisos para acelerar el diagnóstico en producción.

---

### Manejo de errores y excepciones (RFC 7807)

**Por qué:** `GlobalExceptionHandler` cubría los casos básicos pero dejaba sin mapear errores de servicios externos (Documenso, R2), duplicados de datos y fallos del filtro JWT, resultando en respuestas 500 opacas o códigos de estado semánticamente incorrectos.

**Nuevas clases de excepción:**
- `ExternalServiceException` — para fallos de Documenso y Cloudflare R2 → **502 Bad Gateway**
- `DuplicateResourceException` — para email o NIT duplicado → **409 Conflict** (antes devolvían 400)

**Nuevos handlers en `GlobalExceptionHandler`:**

| Excepción | HTTP | Título |
|-----------|------|--------|
| `ExternalServiceException` | 502 | External service error |
| `DuplicateResourceException` | 409 | Duplicate resource |
| `AuthenticationException` (Spring Security) | 401 | Authentication required |
| `IOException` (multipart truncado) | 400 | File read error |

**`JwtAuthenticationFilter` — fix crítico:**
Si `validateToken()` devuelve `true` pero `buildAuthentication()` falla (claims nulos o malformados), el catch block antes limpiaba el contexto pero dejaba pasar el request → Spring Security devolvía 403 en vez de 401. Ahora: `response.sendError(401)` + `return`.

**`DocumensoServiceImpl` — envolvimiento de llamadas externas:**
- Reemplazados todos los `Objects.requireNonNull(response, …)` por lanzamiento de `ExternalServiceException` (antes lanzaban `NullPointerException` no capturado → 500).
- Cada bloque `documensoRestClient.*` ahora envuelve `RestClientException` en `ExternalServiceException`.
Afecta: `sendViaTemplate`, `fetchTemplateRecipients`, `createEnvelope`, `distribute`, `downloadSignedPdf`.

**Correcciones de tipo de excepción:**

| Lugar | Antes | Ahora | HTTP |
|-------|-------|-------|------|
| `AuthServiceImpl.register()` | `IllegalArgumentException` | `DuplicateResourceException` | 409 |
| `UserServiceImpl.createManagedUser()` | `IllegalArgumentException` | `DuplicateResourceException` | 409 |
| `CompanyController.createCompany()` | `IllegalArgumentException` | `DuplicateResourceException` | 409 |
| `UserController.listByRole()` (rol inválido) | `ResourceNotFoundException` | `IllegalArgumentException` | 400 |

**Archivos nuevos:**
- `shared/exception/ExternalServiceException.java`
- `shared/exception/DuplicateResourceException.java`

**Archivos modificados:**
- `shared/exception/GlobalExceptionHandler.java`
- `config/JwtAuthenticationFilter.java`
- `service/impl/DocumensoServiceImpl.java`
- `service/impl/AuthServiceImpl.java`
- `service/impl/UserServiceImpl.java`
- `controller/CompanyController.java`
- `controller/UserController.java`

**Tests:** `UserServiceImplTest.throwsWhenEmailAlreadyExists` actualizado para esperar `DuplicateResourceException`.

---

### Bugs críticos — Máquina de estados

**Bug 1 — `startEvaluation` ignoraba la regla de mínimo 3 visitas**

**Por qué:** `PracticeVisitRepository.countByAgreementId()` existía con su javadoc (`"Used to enforce the minimum three (3) visits rule"`) pero nunca era llamado desde `startEvaluation()`. Un coordinador podía mover el convenio a EVALUATION sin que el asesor hubiera registrado ninguna visita, violando la Resolución 002-2024.

**Fix:** `startEvaluation()` ahora consulta el conteo antes de transicionar:
```
< 3 visitas → IllegalStateException → 409 Conflict
```

**Bug 2 — NPE en `uploadDocument` y `gradeAgreement` cuando asesor o tutor no asignados**

**Por qué:** Tres sitios hacían `agreement.getAcademicAdvisor().getId()` o `agreement.getCompanyRep().getId()` sin verificar null. Como los campos `academicAdvisorId` y `companyRepId` son opcionales en `createAgreement`, si el acuerdo se creó sin ellos la llamada explotaba en `NullPointerException` → 500 opaco. Ahora devuelven 403 con mensaje descriptivo.

Sitios corregidos:
- `gradeAgreement` rama `ACADEMIC_ADVISOR` — null check en `academicAdvisor`
- `gradeAgreement` rama `COMPANY_TUTOR` — null check en `companyRep`
- `uploadDocument` bloque de documentos de empresa — null check en `companyRep`

**Archivos modificados:**
- `service/impl/AgreementServiceImpl.java` — inyección de `PracticeVisitRepository`, validación en `startEvaluation`, null checks

**Tests actualizados:**
- `StartEvaluation.transitionsActiveToEvaluation` → renombrado a `transitionsActiveToEvaluationWithEnoughVisits`; agrega stub `countByAgreementId = 3`
- `StartEvaluation.throwsWhenFewerThanThreeVisits` — nuevo: stub `countByAgreementId = 2` → espera `IllegalStateException`

**Total: 39 tests, 0 fallos.**

---

### Pendientes para revisión antes de cierre MVP

> Estos ítems no bloquean el flujo principal pero deben resolverse antes de considerar el backend completo.

| # | Ítem | Impacto | Estado |
|---|------|---------|--------|
| 1 | **Endpoint descarga de documentos R2** — no existe `GET /agreements/{id}/documents/{type}`. La secretaría y coordinación no pueden ver los archivos para validarlos. | Los revisores operan a ciegas durante `ADMIN_REVIEW` y `COORDINATION_REVIEW`. | Pendiente |
| 2 | **Validación de rol al asignar asesor/tutor** — `loadUser()` en `createAgreement` no verifica que `academicAdvisorId` tenga rol `ACADEMIC_ADVISOR` ni que `companyRepId` tenga rol `COMPANY_TUTOR`. Cualquier usuario puede ser asignado. | Bug silencioso; falla tarde (en `gradeAgreement`) en vez de temprano. | Pendiente |
| 3 | **Constancia de culminación (FINISHED)** — sin endpoint `GET /agreements/{id}/certificate`. El convenio llega a FINISHED con la nota calculada pero sin output descargable para el estudiante. | Flujo completo sin acción de cierre. Explícitamente aplazado. | Aplazado |

---

## [0.4.0] — 2026-04-21

### Contexto
Revisión de la máquina de estados para alinearla con la Resolución CF No. 002 de 2024. Se corrigen los estados terminales, las ventanas de carga de documentos, el comportamiento del rechazo administrativo y los actores que intervienen en cada fase.

---

### Máquina de estados revisada

**Por qué:** El estado `COMPLETED` no reflejaba correctamente el flujo normativo. La evaluación final es una fase diferenciada con sus propias reglas de negocio, y el cierre del convenio (`FINISHED`) sólo debe ocurrir cuando ambas calificaciones han sido registradas.

**Flujo actualizado:**
```
DRAFT → ADMIN_REVIEW → COORDINATION_REVIEW → PENDING_SIGNATURE → ACTIVE → EVALUATION → FINISHED
              ↓                    ↓
          (vuelve a DRAFT)     REJECTED (terminal)
```

**Archivos modificados:**
- `model/entity/AgreementStatus.java` — reemplaza `COMPLETED` con `EVALUATION` y `FINISHED`; javadoc actualizado con el nuevo diagrama
- `service/AgreementService.java` — `completeAgreement()` renombrado a `startEvaluation()` (ACTIVE → EVALUATION)
- `service/impl/AgreementServiceImpl.java` — implementación de `startEvaluation()`; `gradeAgreement()` ahora valida estado `EVALUATION` y auto-transiciona a `FINISHED` cuando ambas notas están presentes
- `controller/AgreementController.java` — endpoint `POST .../complete` reemplazado por `POST .../start-evaluation`

**Archivo nuevo:**
- `db/migration/V1.0.5__State_machine_revision.sql` — actualiza el CHECK constraint de `status` y agrega columnas de documentos de empresa

---

### Rechazo administrativo no terminal

**Por qué:** La secretaría académica debe poder devolver el convenio al estudiante para correcciones sin cerrarlo definitivamente. Solo el rechazo en coordinación es terminal.

**Regla:**
- `ADMIN_REVIEW` → rechazar: el convenio vuelve a `DRAFT` con `rejectionReason` visible al estudiante. Se limpia al reenviar.
- `COORDINATION_REVIEW` → rechazar: estado terminal `REJECTED`.

**Archivos modificados:**
- `service/impl/AgreementServiceImpl.java` — `rejectAgreement()` diferencia por estado anterior; `submitForReview()` limpia `rejectionReason` al reenviar
- `controller/AgreementController.java` — rol `SECRETARY` añadido a `@PreAuthorize` de `/approve` y `/reject`

---

### Ventanas de carga de documentos corregidas

**Por qué:** El reglamento establece momentos precisos para cada documento: los soportes legales se presentan al iniciar la solicitud (DRAFT), y los documentos de afiliación se entregan en el primer mes de práctica (PENDING_SIGNATURE).

**Ventanas actualizadas:**

| Documento | Estado permitido | Actor |
|-----------|-----------------|-------|
| `CV` | `DRAFT` | `STUDENT` |
| `NIT`, `RUT`, `CAMARA_COMERCIO` | `DRAFT` | `COMPANY_TUTOR` |
| `CONTRACT`, `NATIONAL_ID`, `EPS`, `ARL`, `WORK_PLAN` | `PENDING_SIGNATURE` | `STUDENT` |

**Archivos modificados:**
- `model/dto/DocumentType.java` — añade `NIT`, `RUT`, `CAMARA_COMERCIO`
- `model/entity/Agreement.java` — añade campos `nitFileKey`, `rutFileKey`, `camaraComercioFileKey`
- `model/dto/AgreementResponse.java` — expone los 9 campos de documentos (los 6 originales + los 3 nuevos de empresa; antes no estaban en la respuesta)
- `service/impl/AgreementServiceImpl.java` — `uploadDocument()` aplica las nuevas ventanas y valida el rol por tipo de documento
- `controller/AgreementController.java` — rol `COMPANY_TUTOR` añadido al `@PreAuthorize` del endpoint de carga

---

### Tests unitarios actualizados

**Por qué:** Los tests existentes referenciaban estados y comportamientos que cambiaron en esta versión.

**Cambios:**
- `GradeAgreement` — los tests positivos usan estado `EVALUATION`; el test `computesFinalGrade` verifica la auto-transición a `FINISHED`
- `CompleteAgreement` → renombrado a `StartEvaluation`; verifica transición ACTIVE → EVALUATION
- `RejectAgreement` — separado en dos tests: `adminReviewRejectionReturnsAgreementToDraft` y `coordinationReviewRejectionIsTerminal`
- `TestFixtures.dummyAgreementResponse()` — actualizado al nuevo constructor del record (35 parámetros)

**Total: 38 tests, 0 fallos.**

---

## [0.3.0] — 2026-04-21

### Contexto
Completa las 8 áreas funcionales pendientes del MVP: almacenamiento de documentos en Cloudflare R2, registro de visitas de seguimiento, calificación de convenios, subida de documentos por el estudiante, actualización de empresa, creación de usuarios por coordinador, configuración global de Swagger Bearer Auth y suite de tests unitarios.

---

### Almacenamiento Cloudflare R2

**Por qué:** Los documentos (CV, contrato, ARL, etc.) deben persistir en object storage compatible con S3, sin depender de AWS.

**Archivos nuevos:**
- `config/R2Config.java` — bean `S3Client` con `endpointOverride`, `StaticCredentialsProvider`, `Region.of("auto")` y `pathStyleAccessEnabled(true)`
- `service/StorageService.java` — interfaz: `String upload(String key, String contentType, byte[] data)`
- `service/impl/R2StorageServiceImpl.java` — implementación; wraps `S3Exception` en `RuntimeException`

**Archivos modificados:**
- `pom.xml` — dependencia `software.amazon.awssdk:s3:2.26.12`
- `application.yml` — bloque `app.storage.*` (`endpoint-url`, `bucket`, `access-key`, `secret-key`); valores leídos de variables de entorno (`R2_ENDPOINT_URL`, `R2_BUCKET`, `R2_ACCESS_KEY`, `R2_SECRET_KEY`)

---

### OpenAPI — Bearer Auth global

**Por qué:** `@SecurityRequirement(name = "bearerAuth")` ya estaba en todos los controllers pero el esquema no estaba definido; el candado no aparecía en Swagger UI.

**Archivo nuevo:**
- `config/OpenApiConfig.java` — bean `OpenAPI` que define el esquema `bearerAuth` (HTTP Bearer JWT) y lo aplica globalmente con `addSecurityItem`

---

### Registro de visitas de seguimiento

**Por qué:** Resolución 002-2024 exige mínimo 3 visitas por convenio activo; el asesor debe registrarlas en el sistema.

**Archivos nuevos:**
- `model/dto/CreateVisitRequest.java` — `visitDate`, `visitType (VisitType)`, `observations` (10–2000 chars)
- `model/dto/VisitResponse.java` — `id`, `agreementId`, `advisorEmail`, `visitDate`, `visitType`, `observations`, `createdAt`
- `service/VisitService.java` — `listVisits()` y `registerVisit()`
- `service/impl/VisitServiceImpl.java` — `registerVisit` valida estado `ACTIVE` y que el asesor sea el asignado al convenio

**Nuevos endpoints** en `AgreementController`:

| Método | Ruta | Rol |
|--------|------|-----|
| `GET` | `/api/v1/agreements/{id}/visits` | `COORDINATOR`, `ACADEMIC_ADVISOR`, `ADMIN` |
| `POST` | `/api/v1/agreements/{id}/visits` | `ACADEMIC_ADVISOR` |

---

### Calificación de convenios

**Por qué:** Al finalizar la práctica, tanto el asesor académico como el tutor de empresa deben registrar una nota. La nota final es el promedio de ambas.

**Archivo nuevo:**
- `model/dto/GradeRequest.java` — `grade: BigDecimal` (`@DecimalMin("0.0")` / `@DecimalMax("5.0")`)

**Archivos modificados:**
- `service/AgreementService.java` — firma `gradeAgreement(Long id, GradeRequest, JwtUser)`
- `service/impl/AgreementServiceImpl.java`:
  - `ACADEMIC_ADVISOR` → graba `advisorGrade`; `COMPANY_TUTOR` → graba `companyGrade`
  - Cuando ambas están presentes: `finalGrade = (advisorGrade + companyGrade).divide(2, 1, HALF_UP)`
  - Lanza `IllegalStateException` si el convenio no está `ACTIVE` o si el rol ya envió su nota
  - Lanza `AccessDeniedException` si el usuario no está asignado al convenio o tiene un rol no permitido
- `controller/AgreementController.java` — `PUT /api/v1/agreements/{id}/grade` (`ACADEMIC_ADVISOR`, `COMPANY_TUTOR`)

---

### Subida de documentos

**Por qué:** El estudiante debe adjuntar documentos (CV, contrato firmado, ARL, EPS, etc.) según la etapa del convenio.

**Archivo nuevo:**
- `model/dto/DocumentType.java` (enum) — `CV`, `CONTRACT`, `NATIONAL_ID`, `EPS`, `ARL`, `WORK_PLAN`

**Reglas de ventana de carga:**
- `CV` → permitido en `DRAFT`, `ADMIN_REVIEW`, `COORDINATION_REVIEW`
- resto → solo en `ACTIVE`

**Archivos modificados:**
- `service/AgreementService.java` — firma `uploadDocument(Long id, DocumentType, MultipartFile, JwtUser) throws IOException`
- `service/impl/AgreementServiceImpl.java` — genera clave `agreements/{id}/{type}/{UUID}.{ext}`, llama `storageService.upload()`, actualiza el campo `*FileKey` correspondiente en `Agreement`
- `controller/AgreementController.java` — `POST /api/v1/agreements/{id}/documents/{type}` (`STUDENT`, `multipart/form-data`)

---

### Actualización de empresa

**Por qué:** Los datos de contacto del representante pueden cambiar después del registro inicial; el NIT es inmutable.

**Archivo nuevo:**
- `model/dto/UpdateCompanyRequest.java` — `legalName`, `representativeName`, `@Email representativeEmail` (todos opcionales; NIT ausente)

**Archivo modificado:**
- `controller/CompanyController.java` — `PUT /api/v1/companies/{id}` (`COORDINATOR`, `ADMIN`); aplica solo campos no-nulos y no-vacíos

---

### Creación de usuarios por coordinador

**Por qué:** El coordinador necesita crear cuentas para asesores, tutores y secretarios desde el panel. No puede crear `ADMIN`, `COORDINATOR` ni `STUDENT` por esta vía.

**Archivos nuevos:**
- `model/dto/AllowedRole.java` (enum) — `ACADEMIC_ADVISOR`, `COMPANY_TUTOR`, `SECRETARY`
- `model/dto/CreateUserRequest.java` — `email`, `password` (≥8 chars), `universityId`, `role: AllowedRole`
- `service/UserService.java` — `createManagedUser(CreateUserRequest, JwtUser)`
- `service/impl/UserServiceImpl.java`:
  - `COORDINATOR` usa su propio `universityId`; `ADMIN` usa `request.universityId()`
  - Valida email duplicado; lanza `ResourceNotFoundException` si la universidad no existe
  - Password hasheado con BCrypt antes de persistir

**Archivo modificado:**
- `controller/UserController.java` — `POST /api/v1/users` (`COORDINATOR`, `ADMIN`); retorna `201 Created`

---

### Tests unitarios

**Por qué:** Garantizar regresiones detectables sin levantar contexto Spring ni base de datos.

**Stack:** JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`) + AssertJ. Cobertura medida con JaCoCo.

**JaCoCo:** actualizado `0.8.12 → 0.8.13` para soporte de Java 25 (class file major version 69).

**Archivos nuevos:**

| Archivo | Tests |
|---------|-------|
| `util/TestFixtures.java` | Fábrica estática: `university`, `user`, `student`, `company`, `activeAgreement`, `jwtUser`, `dummyAgreementResponse` |
| `service/impl/AgreementServiceImplTest.java` | 19 tests: `GradeAgreement` ×8, `SubmitForReview` ×4, `CompleteAgreement` ×2, `RejectAgreement` ×2, `TenantIsolation` ×3 |
| `service/impl/VisitServiceImplTest.java` | 8 tests: `RegisterVisit` ×4, `ListVisits` ×4 |
| `service/impl/UserServiceImplTest.java` | 8 tests: tenant routing, email duplicado, universidad inexistente, mapeo de los 3 roles (`@ParameterizedTest`), encoding de password |

**Total: 37 tests, 0 fallos.**

---

## [0.2.0] — 2026-04-19

### Contexto
Esta versión completa el backend MVP del flujo principal de convenios de práctica profesional. Incluye la reescritura completa de la integración con Documenso (v1 → v2), las APIs de soporte necesarias para el frontend, el registro de usuarios y el cierre formal del ciclo de práctica.

---

### Integración Documenso v2

**Por qué:** La versión v1 de la API fue deprecada. El contrato de la API cambió significativamente (endpoints, estructura de payload, manejo de webhooks).

**Cambios:**
- `DocumensoServiceImpl` reescrito completamente para usar `/api/v2`
- Dos modos de operación:
  - **Template mode** (`app.documenso.template-id` configurado): usa `POST /envelope/use` con una plantilla pre-armada en Documenso. No genera PDF — los campos de firma los define la plantilla.
  - **Direct mode** (sin template): sube el PDF generado con `POST /envelope/create` + `POST /envelope/distribute`
- Corrección del tipo de campo de firma: `"SIGNATURE"` en mayúsculas (la API v2 lo requiere)
- Verificación del webhook corregida: Documenso envía el secret como string plano en `X-Documenso-Secret` (no HMAC)
- Payload del webhook usa clave `payload`, no `data`
- `DocumensoWebhookVerifier` usa `MessageDigest.isEqual` para comparación en tiempo constante

**Archivos modificados:**
- `service/DocumensoService.java`
- `service/impl/DocumensoServiceImpl.java`
- `config/DocumensoConfig.java`
- `config/DocumensoWebhookVerifier.java`
- `controller/SignatureWebhookController.java`
- `model/dto/SignatureWebhookPayload.java`
- `resources/application.yml` — añadidos `webhook-secret` y `template-id`

---

### Descarga del PDF firmado

**Por qué:** Una vez que todos firman, el documento firmado vive en Documenso. El frontend necesita poder descargarlo sin exponer el token de API.

**Resultado:** Endpoint proxy que obtiene el PDF firmado desde Documenso y lo devuelve al cliente autenticado.

**Nuevo endpoint:**
```
GET /api/v1/agreements/{id}/document
```
Flujo interno: `GET /envelope/{envelopeId}` → obtiene `envelopeItems[0].id` → `GET /envelope/item/{itemId}/download?version=signed` → stream PDF.

**Archivos modificados:**
- `service/DocumensoService.java` — añadido `downloadSignedPdf()`
- `service/impl/DocumensoServiceImpl.java` — implementación
- `controller/AgreementController.java` — endpoint

---

### APIs de soporte (dropdowns del frontend)

**Por qué:** Sin estas APIs el frontend no puede poblar los formularios para crear un convenio.

**Nuevos endpoints:**

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/v1/companies` | Lista empresas de la universidad |
| `POST` | `/api/v1/companies` | Registra una nueva empresa |
| `GET` | `/api/v1/students` | Lista estudiantes de la universidad |
| `GET` | `/api/v1/users?role=ACADEMIC_ADVISOR` | Lista usuarios por rol |

Todos respetan el tenant isolation — solo devuelven datos de la universidad del usuario autenticado.

**Archivos nuevos:**
- `controller/CompanyController.java`
- `controller/StudentController.java`
- `controller/UserController.java`
- `model/dto/CompanyResponse.java`
- `model/dto/CreateCompanyRequest.java`
- `model/dto/StudentSummaryResponse.java`
- `model/dto/UserSummaryResponse.java`
- `repository/UniversityRepository.java`
- `repository/AcademicProgramRepository.java`

**Archivos modificados:**
- `repository/CompanyRepository.java` — añadidos `findByUniversityId` y `existsByNit`
- `repository/StudentRepository.java` — añadido `findByUniversityId`
- `repository/UserRepository.java` — añadido `findByUniversityIdAndRole`

---

### Estado COMPLETED y cierre de práctica

**Por qué:** El ciclo de vida de un convenio necesita un estado terminal formal además de `ACTIVE`. Permite que la coordinación cierre oficialmente una práctica concluida.

**Resultado:** Nuevo estado `COMPLETED` y endpoint de cierre. El enum Java ya estaba desfasado — el CHECK constraint de la DB ya incluía `COMPLETED` desde `V1.0.0`.

**Flujo completo actualizado:**
```
DRAFT → ADMIN_REVIEW → COORDINATION_REVIEW → PENDING_SIGNATURE → ACTIVE → COMPLETED
             ↓                  ↓
          REJECTED           REJECTED
```

**Nuevo endpoint:**
```
POST /api/v1/agreements/{id}/complete   (COORDINATOR, ADMIN)
```

**Archivos modificados:**
- `model/entity/AgreementStatus.java` — añadido `COMPLETED`
- `service/AgreementService.java` — añadido `completeAgreement()`
- `service/impl/AgreementServiceImpl.java` — implementación
- `controller/AgreementController.java` — endpoint

---

### Registro de usuarios

**Por qué:** Los usuarios solo podían crearse mediante migraciones SQL. El frontend necesita un flujo de auto-registro para estudiantes, asesores y representantes de empresa.

**Resultado:** Tres endpoints públicos de registro (retornan JWT directamente) y un endpoint de consulta de programas académicos para el formulario.

**Nuevos endpoints:**

| Método | Ruta                            | Descripción                        |
|--------|---------------------------------|------------------------------------|
| `POST` | `/auth/register/student`        | Crea usuario + perfil Student      |
| `POST` | `/auth/register/advisor`        | Crea usuario ACADEMIC_ADVISOR      |
| `POST` | `/auth/register/company-tutor`  | Crea usuario COMPANY_TUTOR         |
| `GET` | `/auth/programs/{universityId}` | Lista programas activos (dropdown) |

Roles no registrables vía API: `ADMIN`, `COORDINATOR` (se crean por seed o manualmente).

**Body mínimo para `/register/student`:**
```json
{
  "email": "estudiante@uni.edu.co",
  "password": "minimo8chars",
  "universityId": 1,
  "fullName": "Juan Pérez",
  "documentNumber": "1234567890",
  "phoneNumber": "3001234567",
  "academicProgramId": 1,
  "currentSemester": 9
}
```

**Body para `/register/advisor` y `/register/company-tutor`:** solo `email`, `password`, `universityId`.

**Archivos nuevos:**
- `model/dto/RegisterRequest.java`

**Archivos modificados:**
- `service/AuthService.java` — añadido `register()`
- `service/impl/AuthServiceImpl.java` — implementación con `PasswordEncoder` + creación de `Student` si aplica
- `controller/AuthController.java` — tres endpoints de registro + programas

---

### Correcciones menores

- `activateAgreement()` en la interfaz `AgreementService` corregido a `void` (desync con la implementación)
- Template HTML `convenio_practica.html` corregido a XHTML estricto (`<meta/>`, `<br/>`) para compatibilidad con OpenHTMLToPDF
- `DocumensoServiceImpl` migrado de `@RequiredArgsConstructor` a constructor explícito para inyección de `@Value`

---

## [0.1.0] — Base del proyecto

- Autenticación JWT (login, filtro, `JwtUser` principal)
- Máquina de estados del convenio: DRAFT → ACTIVE + REJECTED
- Generación de PDF con Thymeleaf + OpenHTMLToPDF
- Multi-tenancy por `university_id` en todas las entidades
- Validaciones de normativa (Resolución 002-2024): créditos, seminarios, duración, horas
- Auditoría de estados (`agreement_status_history`)
- Migraciones Flyway V1.0.0 → V1.0.4
- Global exception handler (RFC 7807 Problem Details)
