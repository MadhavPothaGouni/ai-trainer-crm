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
  product/        the shared product catalog - no ownerId (see the entity's
                  javadoc for why it skips ScopeAuthorizationService entirely)
  quote/          priced proposals tied to one Opportunity, plus their line
                  items; totals are recomputed server-side on every line
                  item add/edit/remove (QuoteService#recomputeTotals)
  order/          confirmed sales, optionally converted from a Quote
                  (OrderService#createFromQuote clones its line items
                  verbatim); DRAFT -> CONFIRMED is gated on ORDER:APPROVE,
                  a separate permission from plain ORDER:UPDATE - see
                  Order's javadoc
  invoice/        bills generated from exactly one Order
                  (InvoiceService#generateFromOrder); DRAFT -> SENT is
                  gated on INVOICE:APPROVE; SENT -> PAID happens
                  automatically, driven by payment/ - never set directly
  payment/        payments recorded against an Invoice; every record/delete
                  recomputes the parent invoice's amountPaid and flips its
                  status once fully covered (InvoiceService#applyAmountPaid)
  campaign/       marketing campaigns plus Campaign Members - a Lead or a
                  Contact (never both, enforced by a DB check constraint)
                  tracked through an engagement funnel; CampaignController
                  is the first real implementation of the :EXPORT
                  permission anywhere in this codebase (CSV, via
                  common.util.CsvWriter)
  knowledgearticle/ support/help-center articles: auto-generated unique
                  slugs, tags (@ElementCollection, deliberately EAGER -
                  see the entity's javadoc for why that's the right call
                  here and not the LazyInitializationException trap
                  ApiKeyService hit earlier), a DRAFT -> PUBLISHED ->
                  ARCHIVED lifecycle, and a view counter that increments on
                  every read
  report/         read-only aggregation queries over Opportunity/Lead -
                  pipeline value by stage, the lead conversion funnel, and
                  a per-rep leaderboard. No entity of its own (a report is
                  a view over other modules' data); see the module's own
                  javadoc for why it has a second, report-only repository
                  interface per aggregated entity instead of adding these
                  queries onto OpportunityRepository/LeadRepository
  dashboard/      the saved-report/dashboard-builder feature report/'s own
                  javadoc flagged as future work - a Dashboard is a named,
                  owner-scoped set of DashboardWidgets, each just naming
                  one of ReportService's three queries plus grid layout;
                  DashboardService#getData pulls every widget's numbers
                  live on each read rather than storing/caching any report
                  data itself, so a saved dashboard is a saved view, never
                  a stale snapshot
  customfield/    platform extensibility: Custom Objects (admin-defined
                  generic entities - one built-in Name field, everything
                  else comes from attached fields) and Custom Fields
                  (attachable to a Custom Object OR to a fixed allow-list of
                  standard entities - ACCOUNT/CONTACT/LEAD/OPPORTUNITY/
                  CAMPAIGN - never both, same exactly-one-of-target
                  polymorphism CampaignMember introduced for lead/contact);
                  values are a classic EAV table, stored as text and
                  parsed/validated against each field's declared FieldType
                  in CustomFieldService#parseAndValidate
  workflow/       automation: fires when a Lead/Contact/Account/Opportunity
                  is created/updated/deleted (matched against the same
                  CrmAuditEvents webhook/audit already consume) and creates
                  a follow-up Activity task - see WorkflowEngineListener's
                  javadoc. Unlike campaign/knowledgearticle/customfield,
                  this IS owner-scoped (OWN/TEAM/ORGANIZATION, no
                  DEPARTMENT) - a workflow belongs to whoever created it,
                  same shape as account/contact/lead/opportunity
  apikey/         programmatic-auth API keys - only a bcrypt hash of the
                  secret is ever stored; a key authenticates as whoever
                  created it (see ApiKeyService's javadoc)
  webhook/        webhook subscriptions, HMAC-signed and dispatched off the
                  exact same CrmAuditEvents the audit module listens to -
                  see WebhookDispatchListener's javadoc
  audit/          domain events -> @Async listener -> audit_events table
  security/       JWT issuing/parsing, UserPrincipal, method security,
                  plus security.apikey - the X-Api-Key request filter
  common/         BaseEntity, exception hierarchy, ApiResponse/ErrorResponse/
                  PageResponse envelopes
  config/         SecurityConfig, CORS, OpenAPI, properties classes
```

Every owner-scoped CRM module (`account`/`contact`/`opportunity`/`lead`/
`activity`/`quote`) follows the same shape: `entity` + `repository` +
`service` + `controller` + `dto`, record-level OWN/TEAM/DEPARTMENT/
ORGANIZATION authorization via `security.authorization.ScopeAuthorizationService`,
and a permission catalog already seeded in `V2__seed_permission_catalog.sql`.
`workflow` and `dashboard` follow the same owner-scoped shape too, minus
DEPARTMENT (not seeded for WORKFLOW/DASHBOARD - see V2's own comment) -
see their module comments above for why they, unlike this session's other
two modules (campaign/customfield), get an `ownerId`.
`product`, `order`, `invoice`, `payment`, `campaign`, and
`knowledgearticle` are shared-org-resource exceptions to that shape - see
`product`'s module comment above; no `ownerId` column, no
`ScopeAuthorizationService` call, static `@PreAuthorize` at TEAM/
DEPARTMENT/ORGANIZATION scope only (no OWN). `order`/`invoice` additionally
get an `APPROVE` action for their "sign off on it" transitions
(`ORDER:APPROVE` on confirm, `INVOICE:APPROVE` on issue); `campaign`/
`knowledgearticle` don't have APPROVE seeded, so all of their status
transitions are plain `UPDATE`, and they instead get an `EXPORT` action -
`CampaignController#export`/`KnowledgeArticleController#export` are the
only two endpoints in the whole codebase that actually implement it (see
`common.util.CsvWriter`); every other `:EXPORT`-seeded resource still has
the permission modeled but unbuilt.
`report` is a different kind of exception: it's read-only and has no
owner-scoped record of its own, so it uses
`ScopeAuthorizationService#visibleOwnerIds` directly against the REPORT
permission to filter its aggregate queries by owner, rather than the
record-level `assertCanAccess` pattern the CRUD modules use. `apikey`,
`webhook`, and `customfield` are a third kind: platform administration,
gated entirely by `API_KEY:*:ORGANIZATION` / `INTEGRATION:*:ORGANIZATION` /
`CUSTOM_FIELD:*:ORGANIZATION` / `CUSTOM_OBJECT:*:ORGANIZATION` (no OWN/
TEAM/DEPARTMENT variant exists for any of these four resources), with no
per-record ownership concept at all - see `ApiKeyController`'s,
`WebhookSubscriptionController`'s, and `CustomFieldController`'s/
`CustomObjectController`'s javadoc. Note `CustomFieldController#/values`
deliberately gates reading/writing a *value on a standard entity's record*
(e.g. an Account) on `CUSTOM_FIELD:*:ORGANIZATION` rather than
`ACCOUNT:UPDATE` - a documented simplification, not an oversight. By
contrast, `dashboard`'s own read path (`DashboardService#getData`) is a
correct use of an *existing* permission, not a shortcut: rendering a
dashboard's widget data delegates straight into `ReportService`, which
enforces REPORT:READ's own OWN/TEAM/ORGANIZATION scope internally - so
viewing a dashboard's numbers requires the caller to hold both DASHBOARD:
READ (the shell) and some level of REPORT:READ (each widget's data), not a
single all-encompassing DASHBOARD permission.

Every resource in `V2__seed_permission_catalog.sql` now has a module built
on top of it.

See the root `README.md` for the RBAC model, multi-tenancy rules, and the
overall system architecture.
