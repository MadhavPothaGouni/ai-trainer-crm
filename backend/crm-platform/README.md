# crm-platform — backend

Spring Boot 3.2.5 / Java 17 modular monolith. One deployable, one database,
organized into feature modules under `com.aitrainercrm.platform` (`auth`,
`organization`, `user`, `role`, `audit`, `security`, `common`, ...) rather than
separate services — see the root `README.md` for why.

## Prerequisites

- JDK 17
- Maven (or use the included `./mvnw` if present, otherwise a local `mvn`)
- PostgreSQL 16, Redis, and RabbitMQ reachable — either run
  `docker compose up postgres redis rabbitmq` from the repo root, or point the
  env vars below at your own instances
- Docker, only for the integration tests (`AbstractIntegrationTest` starts a
  real Postgres via Testcontainers) and for building the container image

## Running locally

```bash
# from the repo root - starts just the three backing services, not the app itself
docker compose up postgres redis rabbitmq

# from backend/crm-platform
mvn spring-boot:run
```

The app starts on `http://localhost:8080` with the `dev` profile active by
default (`SPRING_PROFILES_ACTIVE=dev` — see `application-dev.yml`: SQL
logging on, debug-level app logs). Flyway runs `V1__init_schema.sql` and
`V2__seed_permission_catalog.sql` automatically on startup against whatever
`DB_URL` points at.

- API docs: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

## Configuration

Every setting has a sensible local default (see `application.yml`) and reads
from an environment variable of the same shape:

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` (verbose logging) or `prod` (quiet) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/ai_trainer_crm` | Postgres JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `crm_user` / `crm_password` | Postgres credentials |
| `DB_POOL_SIZE` | `10` | HikariCP max pool size |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / _(empty)_ | Cache backing |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `localhost` / `5672` / `guest` / `guest` | Messaging |
| `JWT_SECRET` | placeholder — **must** be overridden outside local dev | HMAC key signing access tokens, ≥256 bits |
| `JWT_ACCESS_EXPIRATION_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_EXPIRATION_DAYS` | `30` | Refresh token lifetime (rotated + reuse-detected, see `RefreshToken`) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Comma-separated allowed frontend origins |
| `SERVER_PORT` | `8080` | HTTP port |
| `LOG_LEVEL` / `SQL_LOG_LEVEL` | `INFO` / `WARN` | Per-package log levels |

## Testing

```bash
mvn verify
```

Runs both plain unit tests (Mockito) and the `*IntegrationTest` classes in
the same `test` phase (no separate failsafe/integration-test split — see
`.github/workflows/backend-ci.yml`). The integration tests spin up a real
Postgres via Testcontainers (`AbstractIntegrationTest` — a genuine singleton
container shared across test classes, started once in a static initializer;
see that class's javadoc for why it's deliberately *not* using
`@Testcontainers`/`@Container`) and exercise the actual HTTP endpoints
through `MockMvc` with Spring Security's real filter chain, so they catch
things unit tests structurally can't: a wrong request mapping, a security
rule blocking a public endpoint, a Flyway migration that doesn't match the
entities.

JaCoCo coverage reports land in `target/site/jacoco/index.html` after `mvn
verify` (or `mvn test`).

## Building a container image

```bash
docker build -t ai-trainer-crm/backend .
```

Multi-stage build (see `Dockerfile`) — the Maven/JDK build stage never ships,
only the resulting jar goes into a slim `eclipse-temurin:17-jre-alpine`
runtime image, running as a non-root user.

## Module layout

```
src/main/java/com/aitrainercrm/platform/
  auth/           registration, login, refresh-token rotation + reuse detection,
                  password reset, email verification
  organization/   the tenant itself (name, slug, currency, timezone)
  user/           teammate accounts within an organization: invite, roles,
                  status, removal
  role/           RBAC: Permission (resource × action × scope) -> Role -> User
  account/        companies (CRM)
  contact/        people, usually at an account (CRM)
  opportunity/    sales pipeline items ("deals"), tied to an account (CRM)
  lead/           unqualified prospects; convert into account+contact(+opportunity) (CRM)
  activity/       calls/emails/meetings/tasks/notes logged against any of the
                  four CRM entities above - see V4's migration comment for why
                  its related-to reference has no DB foreign key
  audit/          domain events -> @Async listener -> audit_events table
  security/       JWT issuing/parsing, UserPrincipal, method security
  common/         BaseEntity, exception hierarchy, ApiResponse/ErrorResponse/
                  PageResponse envelopes
  config/         SecurityConfig, CORS, OpenAPI, properties classes
```

Every CRM module (`account`/`contact`/`opportunity`/`lead`/`activity`) follows
the same shape: `entity` + `repository` + `service` + `controller` + `dto`,
record-level OWN/TEAM/DEPARTMENT/ORGANIZATION authorization via
`security.authorization.ScopeAuthorizationService`, and a permission catalog
already seeded in `V2__seed_permission_catalog.sql` - the catalog seeds
several resources (`PRODUCT`, `QUOTE`, `CAMPAIGN`, `REPORT`, `WORKFLOW`, ...)
that don't have a module built on top of them yet; see the root README's
Roadmap.

See the root `README.md` for the RBAC model, multi-tenancy rules, and the
overall system architecture.
