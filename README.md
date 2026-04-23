# Convenia API

Gestor SaaS para automatizar el ciclo de vida de los **Convenios de Práctica Profesional** de la **Facultad de Ingeniería — Universidad Remington**, conforme a la **Resolución del Consejo de Facultad No. 002 de 2024**.

> Convenia cubre el flujo de la práctica de extremo a extremo: creación del borrador, validación por secretaría y coordinación, generación del PDF formal, firma electrónica vía **Documenso**, registro de visitas de seguimiento, evaluación 50 %/50 % entre Docente Asesor y Tutor Co-formador, y cierre con nota final.

**Versión actual:** `0.6.0` — Backend MVP completo. Frontend Angular en desarrollo (repositorio `convenia-web/`).

---

## Tabla de contenido

- [Visión general del flujo](#visión-general-del-flujo)
- [Stack](#stack)
- [Estructura de paquetes](#estructura-de-paquetes)
- [Prerrequisitos](#prerrequisitos)
- [Variables de entorno](#variables-de-entorno)
- [Cómo levantar el proyecto local](#cómo-levantar-el-proyecto-local)
- [Migraciones Flyway](#migraciones-flyway)
- [API — endpoints principales](#api--endpoints-principales)
- [Tests](#tests)
- [Documentación de referencia](#documentación-de-referencia)

---

## Visión general del flujo

Máquina de estados del convenio (`AgreementStatus`):

```
DRAFT → ADMIN_REVIEW → COORDINATION_REVIEW → PENDING_SIGNATURE → ACTIVE → EVALUATION → FINISHED
             ↓                    ↓
         (vuelve DRAFT)       REJECTED (terminal)
```

| Estado | Qué ocurre | Actor principal |
|--------|-----------|-----------------|
| `DRAFT` | Estudiante crea el convenio y sube CV. Tutor de empresa sube NIT / RUT / Cámara de Comercio. | `STUDENT`, `COMPANY_TUTOR` |
| `ADMIN_REVIEW` | Secretaría valida documentación; puede devolver a `DRAFT`. | `SECRETARY` |
| `COORDINATION_REVIEW` | Coordinación audita, genera el PDF y lo envía a Documenso. | `COORDINATOR` |
| `PENDING_SIGNATURE` | Estudiante sube afiliaciones (ARL, EPS, cédula, plan de trabajo). Las 3 partes firman en Documenso. | `STUDENT` + Documenso |
| `ACTIVE` | El webhook `DOCUMENT_COMPLETED` activa el convenio. Asesor registra mínimo **3 visitas**. | `ACADEMIC_ADVISOR` |
| `EVALUATION` | Se abre cuando hay 3 visitas. Asesor y Tutor califican cada uno el 50 %. | `ACADEMIC_ADVISOR`, `COMPANY_TUTOR` |
| `FINISHED` | Nota final = `(nota asesor × 0,50) + (nota tutor × 0,50)` — calculada automáticamente. | Sistema |

**Validaciones normativas** (Resolución CF 002-2024):

- Mínimo **80 %** de créditos aprobados (programa profesional) o **70 %** (tecnológico).
- Mínimo **2 seminarios** de práctica completados antes de enviar a revisión.
- Duración mínima **4 meses** (20 h/sem), máxima **12 meses** (48 h/sem). `APPRENTICESHIP` = exactamente 6 meses.
- `start_evaluation` requiere ≥ 3 visitas registradas.
- Escala de calificación: 0,0 – 5,0.

---

## Stack

| Capa | Tecnología |
|------|------------|
| Backend | **Spring Boot 4.0.5** sobre **Java 25** |
| Persistencia | **PostgreSQL** + **Flyway** (migraciones versionadas) + **Hibernate** (`ddl-auto: validate`) |
| Autenticación | **JWT** (HMAC-SHA-256, 24 h) vía `jjwt 0.13` |
| Mapeo DTO ↔ Entity | **MapStruct 1.6** |
| Boilerplate | **Lombok** |
| Almacenamiento | **Cloudflare R2** (S3-compatible) vía AWS SDK v2 |
| Firma electrónica | **Documenso v2** REST API (modo plantilla + modo upload directo) |
| Generación de PDF | **Thymeleaf** + **OpenHTMLToPDF** |
| Documentación | **springdoc-openapi** (Swagger UI) |
| Test | **JUnit 5** + **Mockito** + **AssertJ** + `spring-security-test` |

### Multi-tenancy

Columna `university_id` en todas las tablas relevantes. Los usuarios con rol `ADMIN` tienen `university_id = NULL` (operan a nivel plataforma). El `JwtAuthenticationFilter` inyecta `universityId` en el `SecurityContext` y los services filtran por él en cada consulta.

---

## Estructura de paquetes

```
com.uniremington.api.convenia
├── ConveniaApplication.java        · entry point Spring Boot
├── config/                         · SecurityConfig, OpenApiConfig, AuditingConfig
├── controller/                     · REST controllers (Auth, Agreement, Visit, User, Company, ...)
├── mapper/                         · MapStruct mappers
├── model/
│   ├── dto/                        · Records de request/response
│   ├── entity/                     · @Entity JPA
│   ├── enums/                      · AgreementStatus, UserRole, PracticeModality, ...
│   └── vo/                         · JwtUser y value objects
├── repository/                     · Spring Data JPA
├── service/                        · interfaces + impl/
└── shared/
    ├── audit/                      · AuditableEntity + listener
    └── exception/                  · GlobalExceptionHandler (RFC 7807)
```

---

## Prerrequisitos

- **JDK 25** (Temurin o similar)
- **Maven Wrapper** (`./mvnw`) — incluido en el repo
- **PostgreSQL ≥ 15** — por defecto se espera en `localhost:5433`, base `convenia-stagge-db`
- **Cuenta de Cloudflare R2** con un bucket `convenia-docs`
- **Cuenta de Documenso v2** con API token y webhook secret

---

## Variables de entorno

Todas las variables tienen un valor por defecto para desarrollo en `application.yml`. **Sobrescríbelas en producción** mediante variables de entorno o `.env`.

| Variable | Descripción | Default (dev) |
|----------|-------------|---------------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | PostgreSQL | `localhost` / `5433` / `convenia-stagge-db` |
| `DB_USER` / `DB_PASSWORD` | Credenciales PostgreSQL | `postgres` / `postgres` |
| `SERVER_PORT` | Puerto HTTP del backend | `8080` |
| `APP_JWT_SECRET` | Clave HMAC para firmar JWT (≥ 32 chars) | clave de dev — **rotar en prod** |
| `APP_JWT_EXPIRATION` | Caducidad del JWT en ms | `86400000` (24 h) |
| `APP_ALLOWED_ORIGINS` | Orígenes CORS permitidos (coma-separados) | `http://localhost:4200` y otros |
| `DOCUMENSO_BASE_URL` | API de Documenso | `https://app.documenso.com/api/v2` |
| `DOCUMENSO_TOKEN` | Bearer token de Documenso | — |
| `DOCUMENSO_WEBHOOK_SECRET` | Secreto para verificar webhooks | vacío (sin verificación en dev) |
| `DOCUMENSO_TEMPLATE_ID` | Plantilla de Documenso opcional. Vacío → genera PDF directo. | vacío |
| `app.storage.endpoint-url` | Endpoint de R2 | hardcoded en `application.yml` |
| `app.storage.access-key` / `secret-key` | Credenciales R2 | hardcoded en `application.yml` |

> ⚠️ **Seguridad:** las credenciales de R2 están en el YAML por conveniencia del MVP. Antes de ir a producción, muévelas a `${R2_ACCESS_KEY}` / `${R2_SECRET_KEY}` y rota las actuales.

---

## Cómo levantar el proyecto local

```bash
# 1. Clonar y posicionarse en la raíz
git clone <repo-url>
cd convenia

# 2. Levantar PostgreSQL local (ejemplo con Docker)
docker run -d --name convenia-db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=convenia-stagge-db \
  -p 5433:5432 \
  postgres:18

# 3. Compilar y correr tests
./mvnw clean verify

# 4. Arrancar la aplicación (aplica Flyway automáticamente)
./mvnw spring-boot:run
```

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs
- **Health:** http://localhost:8080/actuator/health *(si se habilita el starter)*

### Usuarios de prueba (seed V1.0.4)

| Email | Password | Rol |
|-------|----------|-----|
| `admin@convenia.app` | `Admin1234!` | `ADMIN` |
| `julianvilla07021+advisor@gmail.com` | `Test1234!` | `ACADEMIC_ADVISOR` |
| `julianvilla07021+rep@gmail.com` | `Test1234!` | `COMPANY_TUTOR` |
| `julianvilla07021+student@gmail.com` | `Test1234!` | `STUDENT` |

Existe un convenio de prueba con `id=1` en estado `ACTIVE` listo para registrar visitas y abrir la fase de evaluación.

---

## Migraciones Flyway

Ubicación: `src/main/resources/db/migration/`. Convención: `V{major}.{minor}.{patch}__{Description}.sql`.

| Versión | Descripción |
|---------|-------------|
| `V1.0.0` | Esquema inicial (users, universities, companies, agreements, students, ...) |
| `V1.0.1` | Seed data base (universidad + admin) |
| `V1.0.2` | Fix de password del admin |
| `V1.0.3` | Compliance normativa (campos de Resolución 002-2024) |
| `V1.0.4` | Test data (usuarios de prueba + agreement de ejemplo) |
| `V1.0.5` | Revisión de la máquina de estados |
| `V1.0.6` | Schema hardening — 10 índices nuevos (partial en `agreements(documenso_document_id)`, composites para dashboards), drop `idx_user_email` redundante, 3 CHECK constraints `NOT VALID` |
| `V1.0.7` | `VALIDATE CONSTRAINT` de V1.0.6 + `users.full_name NOT NULL` con backfill en 4 etapas |

**Diferido post-MVP:** migración a `TIMESTAMPTZ` en las 8 tablas (requiere `LocalDateTime` → `Instant` en `AuditableEntity` y `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`).

---

## API — endpoints principales

Todos los endpoints (excepto `/auth/**` y `/api/v1/webhooks/**`) requieren header `Authorization: Bearer <jwt>`.

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/auth/login` | Login → JWT |
| `POST` | `/auth/register/{role}` | Registro público (`student` \| `advisor` \| `company-tutor`) |
| `GET` | `/auth/programs` | Programas académicos disponibles |
| `GET` | `/api/v1/agreements` | Lista convenios del usuario (filtrados por rol) |
| `POST` | `/api/v1/agreements` | Crear convenio (`DRAFT`) |
| `GET` | `/api/v1/agreements/{id}` | Detalle |
| `POST` | `/api/v1/agreements/{id}/submit` | `DRAFT` → `ADMIN_REVIEW` |
| `POST` | `/api/v1/agreements/{id}/approve` | `ADMIN_REVIEW` → `COORDINATION_REVIEW` |
| `POST` | `/api/v1/agreements/{id}/reject` | Rechazo con razón (depende del estado) |
| `POST` | `/api/v1/agreements/{id}/endorse` | `COORDINATION_REVIEW` → `PENDING_SIGNATURE` (genera PDF + Documenso) |
| `POST` | `/api/v1/agreements/{id}/start-evaluation` | `ACTIVE` → `EVALUATION` (exige 3 visitas) |
| `PUT` | `/api/v1/agreements/{id}/grade` | Calificar (asesor o tutor) |
| `POST` | `/api/v1/agreements/{id}/documents/{type}` | Subir documento a R2 |
| `GET` | `/api/v1/agreements/{id}/documents/{type}` | Descargar documento de R2 |
| `GET` | `/api/v1/agreements/{id}/document` | Descargar PDF firmado de Documenso |
| `GET` / `POST` | `/api/v1/agreements/{id}/visits` | Listar / registrar visitas |
| `POST` | `/api/v1/users` | Crear usuario gestionado (COORDINATOR / ADMIN) |
| `GET` | `/api/v1/users` | Listar usuarios por rol |
| `POST` | `/api/v1/webhooks/signature` | Webhook Documenso (activa convenios al firmar) |

### Manejo de errores — RFC 7807

Todas las excepciones devuelven un `application/problem+json`:

| Código | Excepción | Cuándo |
|--------|-----------|--------|
| `400` | `MethodArgumentNotValidException` | Validación de `@Valid` |
| `401` | `InvalidCredentialsException` | Login fallido o JWT inválido |
| `403` | `AccessDeniedException` | Autorización denegada |
| `404` | `ResourceNotFoundException` | Entidad no existe |
| `409` | `DuplicateResourceException` | Unique constraint |
| `502` | `ExternalServiceException` | Fallo en Documenso / R2 |

---

## Tests

```bash
./mvnw test           # corre JUnit 5 + Mockito (39 tests)
./mvnw verify         # corre tests + reporte JaCoCo en target/site/jacoco/
```

Fixtures centralizados en `src/test/java/com/uniremington/api/convenia/util/TestFixtures.java`.

---

## Documentación de referencia

- **[Resolución CF No. 002 de 2024](./normativa_practicas.md)** — reglamento de Prácticas Profesionales, Facultad de Ingeniería.
- **[`business_rules.md`](./business_rules.md)** — reglas de negocio derivadas de la Resolución.
- **[`AI-agent-context-project.md`](./context-project.md)** — contexto maestro para agentes de IA (stack, flujo, endpoints).
- **[`CHANGELOG.md`](./CHANGELOG.md)** — historial de versiones detallado.
- **[`MER.mermaid`](./MER.mermaid)** — diagrama entidad-relación (Mermaid).
- **[`.rules/rules/springboot.mdc`](./.rules/rules/springboot.mdc)** — convenciones obligatorias de estilo Spring Boot.
- [Documenso v2 API Docs](https://documenso.com/docs/public-api/)
- [Spring Boot 4 Reference](https://docs.spring.io/spring-boot/index.html)

---

## Licencia

Proyecto académico — Universidad Remington. Uso interno.
