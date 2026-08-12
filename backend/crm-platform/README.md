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
  organization/   the tenant itself (name, slug, currency, timezone), plus
                  Team (Sales/Marketing/Support/... groupings with a free-text
                  department string) - Team existed since V1 purely so
                  ScopeAuthorizationService had something to resolve TEAM/
                  DEPARTMENT-scope visibility against, with no management API
                  of its own until V16 shipped TeamController (CRUD) and
                  UserService#updateTeam (assignment) - see V16's migration
                  comment and ScopeAuthorizationService's javadoc for the
                  full backstory
  user/           teammate accounts within an organization: invite, roles,
                  status, removal, and (as of V16) team assignment via
                  PATCH .../users/{id}/team
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
  ticket/         support tickets - the resource that had a full permission
                  set seeded in V2 (same "core CRM resource" group as LEAD/
                  CONTACT/ACCOUNT/OPPORTUNITY/ACTIVITY/QUOTE) but no table,
                  entity, or endpoint anywhere until now; mirrors Account's
                  owner-scoped shape exactly, with a free (non-linear)
                  status transition rather than Lead/Order's one-way state
                  machines - see V14's migration comment and Ticket's javadoc
  email/          logged emails (inbound/outbound) against an Account/
                  Contact/Opportunity/Lead/Ticket - EMAIL_MESSAGE is a
                  genuinely new permission-catalog resource added in V15,
                  not a gap like Ticket was (V2 seeded nothing for it).
                  Coexists with activity/'s existing EMAIL type rather than
                  replacing it - Activity logs "an email happened",
                  EmailMessage captures the structured from/to/direction/
                  sent-at data Activity was never meant to hold. See
                  EmailMessageService's javadoc
  calendar/       scheduled events, optionally tied to a CRM record like
                  email/ - plus CalendarEventAttendee, a real child table
                  (unlike email's comma-separated to/cc addresses) since an
                  attendee has independent identity (internal user vs.
                  external guest) and its own mutable response status, same
                  reasoning campaign/'s CampaignMember documents. See V15's
                  migration comment and CalendarEventService's javadoc
  attachment/     files uploaded against an Account/Contact/Opportunity/
                  Lead/Ticket - owner-scoped exactly like ticket/, required
                  (not optional) related_to_type/related_to_id, unlike
                  calendar/'s. The actual bytes never touch Postgres -
                  attachment.storage.FileStorageService (an interface +
                  LocalFileStorageService, the same swappable-abstraction
                  shape notification.email.EmailService already
                  established) is the only thing that reads/writes them;
                  Attachment.storageKey is an opaque pointer into whatever
                  implementation is active, never returned to a client. See
                  V18's migration comment and LocalFileStorageService's
                  javadoc for why local disk is a documented limitation
                  (not durable across container replicas/redeploys) rather
                  than a production plan
  approval/       named, ordered multi-step sign-off chains (ApprovalRequest
                  + ApprovalStep) requested against a Quote/Order/
                  Opportunity - a genuinely different concept from order/'s
                  and invoice/'s existing single-permission-gated APPROVE
                  status transitions, not a duplicate of them; see V19's
                  migration comment for how the two coexist. Owner-scoped
                  off requestedByUserId exactly like ticket/ and attachment/,
                  plus one carve-out none of the other owner-scoped modules
                  have: whoever is named as the approver on any step of a
                  request can always read that request and act on their own
                  step, full stop, regardless of what scope they hold - the
                  platform's fifth resource-access shape. See
                  ApprovalRequestService's javadoc for the full reasoning
                  and V19's migration comment for why the table has no
                  deleted_at (status reaching CANCELLED/APPROVED/REJECTED
                  already carries that meaning)
  sla/            SLA policies (per-Ticket-priority response/resolution time
                  targets) and automatic escalation when one is breached -
                  SlaPolicy is admin config (SLA_POLICY:*:ORGANIZATION only,
                  same shape as customfield/'s and apikey/'s resources, seeded
                  fresh in V20), TicketSlaStatus is a lazily-created,
                  per-ticket tracking row with no foreign key back to
                  tickets. This module has never once written to the tickets
                  table - it reads Ticket's existing priority/status/
                  resolvedAt/createdAt directly rather than threading SLA
                  bookkeeping through Ticket or TicketService, so "responded"
                  is just "status left OPEN" and "resolved" is
                  resolvedAt != null, no new timestamp invented for either.
                  Home to the platform's first real @Scheduled job
                  (SlaEvaluationService#sweep) - CrmPlatformApplication has
                  carried @EnableScheduling since its very first commit with
                  nothing using it until now. Escalation reuses
                  NotificationService (a new createSystem method, sender-less
                  - Notification's own javadoc had already anticipated this)
                  rather than reassigning the ticket's owner out from under
                  whoever's working it; see V20's migration comment and
                  SlaEvaluationService's javadoc for the full design
  territory/      auto-routes a newly created Lead or Account to an owner via
                  TerritoryRule (a single match-field/operator/value
                  criterion, not a boolean expression tree - a deliberate
                  scope limit, same starting point real products like
                  Salesforce Assignment Rules also began at). TerritoryRule
                  itself is admin config (TERRITORY_RULE:*:ORGANIZATION only,
                  seeded fresh in V21, same shape as sla/'s SlaPolicy), but
                  TerritoryAssignmentListener - the @EventListener that
                  actually does the matching and reassignment - is the one
                  module this session where an @Async listener on the
                  CrmAuditEvents bus deliberately writes back to another
                  module's core ownerId column, rather than staying purely
                  additive the way sla/ (reads Ticket fields, never writes
                  to tickets) or workflow/ (never touches ownership at all)
                  do. Safe here specifically because it only ever fires once,
                  on RecordCreated, before any human has touched the record -
                  there's no onRecordUpdated handler, unlike
                  WorkflowEngineListener, because re-running territory
                  matching against edits to an already-owned record is a
                  different and much riskier feature this module doesn't
                  attempt. A team assignment round-robins across the team's
                  current members using TerritoryRule.lastAssignedUserId as
                  the rotation cursor. See TerritoryRule's and
                  TerritoryAssignmentListener's javadoc and V21's migration
                  comment for the full reasoning
  notification/   a teammate's own in-app inbox (notification.inbox package
                  - distinct from notification.email, the existing
                  transactional-email abstraction auth/ already used for
                  verification/reset links). No Permission.Resource, no
                  ScopeAuthorizationService, no @PreAuthorize scope ladder -
                  a notification's visibility never widens with role the
                  way every other resource's does, so NotificationService
                  just checks recipientUserId == caller directly. See V17's
                  migration comment and Notification's javadoc for the full
                  "why this is a fourth access pattern" reasoning
  importexport/   bulk CSV import/export for Account, Contact, Lead, and
                  Ticket - ACCOUNT/CONTACT/LEAD/OPPORTUNITY/ACTIVITY/QUOTE/
                  TICKET all got IMPORT and EXPORT seeded in V2 alongside
                  their other CRUD actions, but nothing ever implemented
                  IMPORT anywhere in the codebase, and EXPORT only existed
                  for Campaign/Knowledge Article until now - see
                  ImportExportService's javadoc for the full picture,
                  including why its import methods are deliberately NOT
                  @Transactional (a subtle Spring propagation trap that
                  would otherwise silently roll back an entire batch's
                  successes because of one bad row). Ticket support was
                  added after the fact with no change to that design - one
                  more headers constant, one more export/import method
                  pair, one more row-builder
  audit/          domain events -> @Async listener -> audit_events table
  security/       JWT issuing/parsing, UserPrincipal, method security,
                  plus security.apikey - the X-Api-Key request filter
  common/         BaseEntity, exception hierarchy, ApiResponse/ErrorResponse/
                  PageResponse envelopes
  config/         SecurityConfig, CORS, OpenAPI, properties classes
```

Every owner-scoped CRM module (`account`/`contact`/`opportunity`/`lead`/
`activity`/`quote`/`ticket`/`email`/`calendar`/`attachment`/`approval`) follows the same shape:
`entity` + `repository` + `service` + `controller` + `dto`, record-level
OWN/TEAM/DEPARTMENT/ORGANIZATION authorization via
`security.authorization.ScopeAuthorizationService`. The first seven have
their permission catalog seeded in `V2__seed_permission_catalog.sql`;
`email`/`calendar` (EMAIL_MESSAGE/CALENDAR_EVENT) are seeded in `V15` instead,
`attachment` (ATTACHMENT) in `V18`, and `approval` (APPROVAL_REQUEST) in
`V19` - new resources added alongside their module in the same migration,
not a catalog-then-module gap like Ticket's - and all four skip IMPORT
(bulk-CSV-importing a sent-email log, a calendar schedule, a set of file
uploads, or a chain of sign-offs isn't a real workflow the way importing a
contact list is); `approval` additionally skips EXPORT and ASSIGN (see V19's
migration comment for why an approval chain has no meaningful "owner
reassignment" the way a Ticket or Attachment does), so its action set is
just CREATE/READ/UPDATE/APPROVE - the first module since order/invoice to
get an APPROVE action at all, though for a completely different reason (see
the "fifth kind" paragraph below). `email`/`calendar`/`attachment`'s action
set is CREATE/READ/UPDATE/DELETE/EXPORT/ASSIGN, one action short of the
original seven.
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
`CampaignController#export`/`KnowledgeArticleController#export` were the
first two endpoints in the codebase to actually implement it (see
`common.util.CsvWriter`); `importexport/` (below) later added EXPORT *and*
IMPORT for Account, Contact, Lead, and Ticket using the same `CsvWriter`
plus its new counterpart `common.util.CsvParser`. Opportunity, Activity,
and Quote still have IMPORT/EXPORT seeded but unbuilt.
`report` is a different kind of exception: it's read-only and has no
owner-scoped record of its own, so it uses
`ScopeAuthorizationService#visibleOwnerIds` directly against the REPORT
permission to filter its aggregate queries by owner, rather than the
record-level `assertCanAccess` pattern the CRUD modules use. `apikey`,
`webhook`, `customfield`, `sla` (`SlaPolicyController` specifically - see
below), `territory` (`TerritoryRuleController` specifically - see below),
and Team (in `organization/`) are a third kind: platform
administration, gated entirely by `API_KEY:*:ORGANIZATION` /
`INTEGRATION:*:ORGANIZATION` / `CUSTOM_FIELD:*:ORGANIZATION` /
`CUSTOM_OBJECT:*:ORGANIZATION` / `SLA_POLICY:*:ORGANIZATION` /
`TERRITORY_RULE:*:ORGANIZATION` / `TEAM:*:ORGANIZATION` (no OWN/TEAM/
DEPARTMENT variant exists for any of these seven resources - a bit of an
irony for `TEAM` specifically, whose entire purpose is backing other
resources' TEAM/DEPARTMENT scope, but managing *teams themselves* is
still an org-wide admin action, same as managing users or roles), with no
per-record ownership concept at all - see `ApiKeyController`'s,
`WebhookSubscriptionController`'s, `CustomFieldController`'s/
`CustomObjectController`'s, `SlaPolicyController`'s,
`TerritoryRuleController`'s, and `TeamController`'s
javadoc. `sla`'s other controller, `TicketSlaController`, is not part of
this third kind at all - it has no `@PreAuthorize` of its own and instead
reuses the ticket's own `TICKET:READ` scope check inline
(`SlaEvaluationService#getForTicket`), the same "lean on an existing
permission rather than invent a redundant one" reasoning `dashboard`'s own
read path already established below. `territory`'s CRUD service
(`TerritoryRuleService`) is a clean instance of this third kind - the
module's actual routing behavior lives entirely in
`TerritoryAssignmentListener`, an `@EventListener` with no `@PreAuthorize`
of its own at all (nothing about matching a newly created Lead against
active rules and reassigning it is a request a caller makes - it's
triggered purely by the `RecordCreated` event Lead/AccountService already
publish), so `territory`'s permission only ever gates *defining* rules, never
the auto-assignment those rules cause. Note `CustomFieldController#/values`
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
`notification` (in `notification/inbox/`) is a fourth kind, simpler than
all three above: self-scoped, not just unscoped. Platform-administration
resources (`apikey`/`webhook`/`customfield`/`Team`) have exactly one fixed
scope (ORGANIZATION) that every holder shares equally - any ADMIN can
manage any API key. A notification has no such shared scope at all; it is
one specific person's mail, and nothing - no role, no permission, no scope
level - should ever let a second person read it. So it skips the
permission catalog entirely rather than seeding a scope that could only
ever mean "yourself." See `Notification`'s javadoc and V17's migration
comment for the full reasoning, including why the table has no
`deleted_at` (nothing else can ever reference or need to see a
notification besides its one recipient, unlike every soft-deleted record
above).
`approval` (in `approval/`) is a fifth kind: owner-scoped like the very
first group above (real `requestedByUserId`, full OWN/TEAM/DEPARTMENT/
ORGANIZATION ladder, standard `@PreAuthorize` gates), but with one
carve-out layered on top at the service level that none of those modules
need - a named approver can always read the one request they're on and act
on their own step, independent of scope entirely. It's a narrower version
of `notification`'s "only yourself" rule (self-scope for exactly one
relationship - being named an approver - rather than for the whole
resource), which is why it's a variation on the owner-scoped shape instead
of skipping the permission catalog the way `notification` does. See
`ApprovalRequestService`'s javadoc and V19's migration comment for the full
reasoning, including why `APPROVAL_REQUEST:APPROVE` is safe to add to the
default MEMBER role (`RoleService#createDefaultRolesForOrganization`)
without also widening `ORDER:APPROVE`/`INVOICE:APPROVE` - those two aren't
in `isCoreCrmResource`, so they never reach the filter that grant applies
to.

An earlier version of this file incorrectly claimed every resource in
`V2__seed_permission_catalog.sql` had a module built on top of it -
`TICKET` was the one exception, seeded with a full CRUD/EXPORT/IMPORT/
ASSIGN permission set but no `ticket/` package, entity, or controller
anywhere. That gap is now closed (see `ticket/` above): every resource
seeded in `V2__seed_permission_catalog.sql` genuinely does have a module
built on top of it as of this commit, and IMPORT/EXPORT specifically has a
real implementation for four of the seven resources that got it seeded
(`account`/`contact`/`lead`/`ticket`) - Opportunity, Activity, and Quote
still have it modeled but unbuilt. `EMAIL_MESSAGE`/`CALENDAR_EVENT` (see
`email`/`calendar` above), `ATTACHMENT` (see `attachment` above),
`APPROVAL_REQUEST` (see `approval` above), `SLA_POLICY` (see `sla`
above), and `TERRITORY_RULE` (see `territory` above) are a different case
from that gap entirely - each was added to the
permission catalog and given a module in the same migration it was seeded
in (`V15`, `V18`, `V19`, `V20`, `V21`), so there was never a window where any of
them were seeded but unimplemented the way
Ticket was.

See the root `README.md` for the RBAC model, multi-tenancy rules, and the
overall system architecture.
