# Convenia API — Changelog

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
