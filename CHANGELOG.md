# Convenia API — Changelog

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
