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
  forecast/       the inverse of dashboard/'s design choice, deliberately -
                  PipelineSnapshotService#captureDaily (this platform's
                  second real @Scheduled job, after SlaEvaluationService#
                  sweep, and like it a genuinely cross-tenant single-pass
                  query) persists one PipelineSnapshot row per (org, date,
                  owner, stage) once a day, specifically so a trend-over-time
                  view exists that a live query structurally cannot
                  reconstruct after the fact - a closed or reassigned deal
                  no longer looks the way it did the day it was captured. No
                  entity CRUD anywhere: the only writer is the scheduled
                  job, and the two read endpoints (GET /forecast/snapshots,
                  /forecast/trend) reuse REPORT:READ rather than a
                  permission of their own, exactly like dashboard's own read
                  path leans on report/'s. See V22's migration comment for
                  the full "why this isn't a duplicate of report/" reasoning
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
  dedupe/         flags likely-duplicate Lead/Contact/Account pairs on
                  creation (DuplicateDetectionListener, a sixth @EventListener
                  on the CrmAuditEvents bus) and lets a caller merge or
                  dismiss the flagged DuplicateMatch. No DUPLICATE_MATCH
                  permission exists anywhere in the catalog - deliberately:
                  DuplicateMatchController has no @PreAuthorize at all, and
                  DuplicateMatchService instead reuses LEAD/CONTACT/ACCOUNT's
                  own READ (list/get) and UPDATE (merge/dismiss) permissions,
                  checked against BOTH records a pair names via two separate
                  ScopeAuthorizationService#assertCanAccess calls - the first
                  place in this codebase two independent scope checks gate a
                  single write. A dedicated permission would be a real
                  security gap, not a simplification: it would let someone
                  merge two Leads they can't otherwise touch at all. Matching
                  is email-first, name-fallback only when a record has no
                  email (see the listener's javadoc for the full per-entity
                  rule), and - like territory/'s TerritoryAssignmentListener -
                  fires once, at creation, with no onRecordUpdated handler.
                  Unlike territory/, this listener is purely additive; the
                  actual ownerId-changing write happens later, synchronously,
                  when a human calls merge. Merging fans out through four
                  shared relatedTo tables (Activity/Attachment/EmailMessage/
                  CalendarEvent) via one generic reassignGenericRelatedTo
                  helper, plus entity-specific FKs where they exist
                  (Contact.accountId/Opportunity.accountId for an Account
                  merge, Opportunity.primaryContactId for a Contact merge);
                  the absorbed record is soft-deleted, never hard-deleted.
                  See V23's migration comment and DuplicateMatchService's
                  javadoc for the full reasoning
  leadscoring/    admin-defined LeadScoringRule rows (field/operator/value/
                  points, the same match-criterion shape territory_rules
                  established in V21) each contribute points to a Lead
                  whenever their criterion matches it; LeadScoringEngine (the
                  seventh @EventListener on the CrmAuditEvents bus) sums every
                  ACTIVE matching rule and writes the total onto the new
                  Lead.score column. Two deliberate differences from
                  TerritoryRule, its closest precedent - see V24's migration
                  comment for the full reasoning: scoring is cumulative (every
                  matching rule contributes, so there's no "first match wins"
                  and therefore no priority column), and LeadScoringEngine
                  reacts to onRecordUpdated as well as onRecordCreated, since
                  a stale score would actively mislead a prioritization
                  feature the way TerritoryAssignmentListener's and
                  DuplicateDetectionListener's deliberately-one-time behavior
                  doesn't. LEAD_SCORING_RULE is admin configuration
                  (CREATE/READ/UPDATE/DELETE at ORGANIZATION scope only,
                  seeded fresh in V24), the same third-kind shape SLA_POLICY
                  and TERRITORY_RULE already use
  salesgoals/     admin-set revenue/deal-count quotas (SalesGoal) for one
                  period, assigned to exactly one of an individual user or a
                  team - the same "exactly one of two" constraint
                  territory_rules/custom_fields established, this time as a
                  real CHECK constraint (V25) rather than an
                  application-level check. Progress against a goal is always
                  computed LIVE from real Opportunity rows (SalesGoalService
                  #toDto, via a second read-only repository over Opportunity,
                  the same "the owning module has no reason to know about
                  this" reasoning report/'s OpportunityAnalyticsRepository
                  documents) - the inverse of forecast/'s choice to
                  materialize, and deliberately so: a goal's period is almost
                  always still open when someone checks it, so there's
                  nothing yet to snapshot. Two access patterns coexist in one
                  module for the first time this session: full CRUD is
                  admin-config (SALES_GOAL:*:ORGANIZATION, the third kind
                  sla/territory/leadscoring already use), but
                  GET /sales-goals/mine skips permissions entirely and just
                  returns the caller's own assigned goals (individually or
                  via their current team) - the fourth-kind, notification-
                  style self-scope pattern, reused here instead of invented
                  fresh. See V25's migration comment and SalesGoalService's
                  javadoc for the full reasoning
  savedviews/     a teammate's own named filter+sort presets (SavedView) for
                  a standard CRM list page - LEAD/CONTACT/ACCOUNT/
                  OPPORTUNITY/TICKET. The purest instance yet of the
                  fourth-kind, notification-style self-scope pattern: no
                  Permission.Resource, no ScopeAuthorizationService, no
                  @PreAuthorize anywhere in SavedViewController, and unlike
                  salesgoals/'s "seventh kind" (which only applies this
                  pattern to one endpoint out of six), every single action
                  here - list/create/update/delete/setDefault - is scoped to
                  ownerUserId with nothing else to check. filters is stored
                  as an opaque JSON blob, same "the frontend owns the shape"
                  reasoning attachment/'s storageKey documents - the service
                  never parses it, only checks it's non-blank. At-most-one-
                  default-per-owner-per-entity-type is enforced the same way
                  dashboard/'s Dashboard already does: a partial unique index
                  (V26) plus SavedViewService#setDefault's unset-then-set-
                  with-saveAndFlush ordering, reused verbatim rather than
                  re-derived - see DashboardService#setDefault's javadoc for
                  why flush order (not setter-call order) is what the unique
                  index actually cares about
  emailtemplate/  reusable, organization-wide {{token}}-placeholder email
                  templates, mail-merged against a real Contact/Lead/
                  Account/Opportunity by EmailTemplateService#render. Like
                  product/'s Product, there's no ownerId - EMAIL_TEMPLATE is
                  seeded (V27) at TEAM/DEPARTMENT/ORGANIZATION scope only
                  (no OWN), and EmailTemplateService does no
                  ScopeAuthorizationService check at all, the second time
                  this "shared catalog, permission-gated-but-no-record-
                  scope" shape has appeared this session. Token
                  substitution itself lives in its own dependency-free
                  emailtemplate.render.TemplateRenderer class (unit-tested
                  with zero Spring context) so EmailTemplateService's only
                  job is resolving which of the caller-supplied contactId/
                  leadId/accountId/opportunityId actually exist in this org
                  and building the flat token-name -> value map
                  TemplateRenderer merges against subject/body. An
                  unresolved token (wrong id, template typo, entity type
                  never supplied) is left in the output exactly as written
                  rather than silently blanked or rejected - see
                  TemplateRenderer's javadoc for why. render() is
                  read-only and ephemeral: nothing is persisted, and a
                  target id is only ever looked up with a real
                  organization-scoped query, never authorized against the
                  target entity's own OWN/TEAM/DEPARTMENT/ORGANIZATION
                  permission ladder - the same trust boundary REPORT:READ
                  already extends across a whole org's aggregate numbers
  region/         a nested org-chart Region tree ("North America" contains
                  "US-West"/"US-East") sitting ABOVE territory/'s existing
                  TerritoryRule, not on top of it - a genuinely different
                  concept, same "different question, not a duplicate"
                  reasoning V21's own migration comment used to distinguish
                  territory/ from workflow/. TerritoryRule answers "who
                  should own this brand-new Lead/Account" (a routing
                  decision fired once by an @EventListener); Region answers
                  "how does our sales org roll up for reporting" (a static
                  grouping of Teams, queried on demand, nothing ever fires
                  it). REGION is admin config (REGION:*:ORGANIZATION only,
                  V28, the same third-kind shape SLA_POLICY/TERRITORY_RULE/
                  LEAD_SCORING_RULE/SALES_GOAL use). parentRegionId is a
                  plain UUID column, not a JPA relationship or even a real
                  self-referencing FK - RegionService#assertNoCycle already
                  has to walk the parent chain in application code to reject
                  cycles (something no FK can express), so there's no extra
                  integrity a DB constraint would buy on top of that. Team
                  gained an optional regionId (V28) - the only link between
                  this tree and any real CRM data - and RegionService#rollup
                  walks a region's full subtree (built from one
                  whole-org-tree query, traversed in memory, the same
                  "resolve fresh on every read" choice SalesGoalService
                  makes for team membership), collects every team pointing
                  at any node in it, then every active user on those teams,
                  then hands that owner set to report/'s existing
                  OpportunityAnalyticsRepository#summarizeByStage rather
                  than adding a third near-identical aggregation repository
                  - unlike salesgoals/'s SalesGoalProgressRepository, a
                  rollup has no period to bound the query by, so the
                  existing unbounded per-stage query already does everything
                  needed. delete() deliberately never cascades: a region
                  with child regions or any team still pointing at it must
                  be reparented/reassigned by the caller first, the same
                  conservative reasoning TeamService#delete documents for
                  never touching the users on a team it deletes
  commission/     automatic sales commission tracking - CommissionEngine
                  (an @EventListener alongside WebhookDispatchListener/
                  AuditEventListener/WorkflowEngineListener/
                  TerritoryAssignmentListener/DuplicateDetectionListener/
                  LeadScoringEngine on the same CrmAuditEvents bus) creates
                  exactly one CommissionRecord the moment an Opportunity's
                  currently-persisted stage is read as CLOSED_WON.
                  Deliberately does NOT try to detect a stage transition from
                  the event itself - RecordUpdated carries no old/new field
                  values (it's intentionally generic across all four CRM
                  entity types) - so instead it reacts to every Opportunity
                  update, reloads current state fresh, and relies on
                  idempotency (CommissionRecordRepository#existsByOpportunityId
                  plus a real uq_commission_records_opportunity unique
                  constraint, V29) to make re-firing on an already-closed deal
                  harmless. CommissionPlan is admin config (COMMISSION_PLAN:
                  *:ORGANIZATION only, V29, the same third-kind shape SLA_
                  POLICY/TERRITORY_RULE/LEAD_SCORING_RULE/SALES_GOAL/REGION
                  use) with an exactly-one-of-ownerUserId/teamId CHECK
                  constraint (chk_commission_plans_exactly_one_target),
                  re-validated in CommissionPlanService the same defense-in-
                  depth SalesGoalService documents for its identical
                  constraint. Plan resolution follows TerritoryAssignment
                  Listener's "no match, nothing happens" default: an
                  individual plan for the Opportunity's owner wins if one
                  exists, else the owner's current team's plan, else no
                  commission record at all. CommissionRecord is fully
                  materialized (dealAmount/rateType/rate/commissionAmount
                  frozen at creation) rather than computed live off the
                  current CommissionPlan the way SalesGoal progress is live -
                  the third instance this session of the live-vs-materialized
                  design fork (after forecast/'s PipelineSnapshot and
                  salesgoals/'s live SalesGoal), for the sharpest reason yet:
                  a commission is money owed, and a later change to a plan's
                  rate must never retroactively change what a rep already
                  earned on a deal they already closed. COMMISSION_RECORD
                  (V29) only seeds READ and APPROVE - never CREATE/UPDATE/
                  DELETE - since CommissionEngine is the only writer of a
                  record's core fields, and the only API mutation is the
                  one-way PENDING -> APPROVED -> PAID status walk (no
                  skipping, no backward moves). GET /commission-records/mine
                  needs no permission at all, the fourth-kind, notification-
                  style self-scoped shape SalesGoalService#myGoals and
                  SavedViewService already established
  gdpr/           GDPR/CCPA-style data-subject rights: export or erase every
                  Contact/Lead in an organization matching one email address
                  (V30). Subjects are identified by email, not a specific
                  Contact/Lead id - "erase everything you have on
                  jane@example.com" is the actual shape of a real request,
                  not "delete Contact <uuid>". DataSubjectRequest is a run
                  log, the same "audit row, not a soft-deletable business
                  record" shape ImportJob already established (no
                  deletedAt column here either) - COMPLETED means the
                  request ran, which is true even when zero records
                  matched, the identical "ran to completion isn't the same
                  as every row succeeding" distinction ImportJob#status's
                  javadoc makes. DATA_SUBJECT_REQUEST is seeded (V30) at
                  ORGANIZATION scope only with the existing READ/EXPORT/
                  DELETE actions reused rather than new ones added - EXPORT
                  gathers data, DELETE erases it, READ browses history -
                  the same platform-administration shape USER/ROLE/
                  AUDIT_LOG already use, and (being absent from RoleService
                  #isCoreCrmResource) automatically admin-only, MEMBER gets
                  none of it by default. Erasure is a genuinely new pattern
                  in this codebase: every other soft delete (Account/
                  Contact/Lead/User/Ticket) only ever sets deletedAt and
                  leaves the row's actual data alone, specifically so FK-
                  referencing history (Activities, Opportunities, the audit
                  trail) keeps resolving - see Contact.deletedAt's own
                  javadoc. DataSubjectRequestService#redactContact/
                  redactLead extend that same reasoning rather than
                  abandoning it: PII columns (name/phone/title/description)
                  are overwritten with a fixed placeholder and email is
                  nulled (not redacted to a fabricated string, which could
                  collide with a real address in
                  ContactRepository#findDuplicateCandidatesByEmail and
                  similar exact-match lookups elsewhere), deletedAt is set
                  only if not already, and the row itself - along with
                  every FK pointing at it - is left alone. Both lookups
                  (ContactRepository/LeadRepository#findByOrganizationIdAndEmailIgnoreCase)
                  deliberately skip the deletedAt filter every other query
                  in this codebase applies, since an already-soft-deleted
                  Contact still holds live PII a right-to-be-forgotten
                  request needs to reach. export() returns a raw
                  ResponseEntity<byte[]> JSON download, the same convention
                  ImportExportController's CSV exports established, just a
                  nested tree instead of a flat table; erase() returns a
                  normal ApiResponse since affected-row counts are data a
                  UI renders, not a file
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
  course/         training catalog (Course) + enrollment (CourseEnrollment) -
                  V31, the first module this session that's actually specific
                  to "ai-trainer-crm" rather than generic CRM surface area any
                  sales platform would need. Course mirrors product/'s shape
                  exactly: shared catalog data, no ownerId, COURSE seeded at
                  TEAM/DEPARTMENT/ORGANIZATION only (no OWN) so CourseService
                  does no ScopeAuthorizationService call, same reasoning
                  ProductService's javadoc gives. CourseEnrollment mirrors
                  ticket/'s shape instead: a normal owner-scoped record, full
                  OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, in
                  RoleService#isCoreCrmResource so MEMBER gets OWN+TEAM by
                  default - except the "owner" column is named userId (the
                  enrolled learner), not ownerId, since "who is this course
                  for" is a different question from "who's working this
                  deal." CourseEnrollmentService#updateProgress re-derives
                  COMPLETED vs FAILED from the submitted score against the
                  Course's own passingScorePercent rather than trusting the
                  caller's requested status outright - the same "verify
                  against real state, don't just trust the event" reasoning
                  CommissionEngine re-reads an Opportunity's actual stage for.
                  uq_course_enrollments_course_user_active (V31) blocks a
                  second concurrent active enrollment in the same course
  certification/  the identical catalog/award pair one level up: Certification
                  (admin-maintained credential catalog, same no-OWN shape as
                  Course) and UserCertification (owner-scoped award record,
                  same full-ladder shape as CourseEnrollment). Unlike
                  CourseEnrollment there's no uniqueness constraint on
                  UserCertification - recertification is normal, so each
                  award is its own historical row rather than something
                  overwritten in place. UserCertification#expiresAt is
                  derived once at award time from Certification#validityMonths
                  and stored, never recomputed live - the same "snapshot,
                  don't let it drift if the catalog definition later changes"
                  reasoning CommissionRecord's frozen dealAmount/rate/
                  commissionAmount columns document
  sequence/       sales engagement sequences (a.k.a. cadences) - V32, the
                  second module this session that's real new functional
                  surface area. Sequence + SequenceStep is the
                  catalog/child-entity pair: Sequence mirrors product/'s
                  no-OWN catalog shape exactly (SEQUENCE seeded at
                  TEAM/DEPARTMENT/ORGANIZATION only), and SequenceStep
                  mirrors QuoteLineItem - a real FK'd child row
                  (on delete cascade), no permission of its own, fully
                  owned by SequenceService's add/update/removeStep, with
                  SequenceDto.from(sequence, steps) embedding the already-
                  loaded step list the same way QuoteDto embeds line items.
                  SequenceEnrollment is the owner-scoped work record - full
                  OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, in
                  RoleService#isCoreCrmResource - except unlike
                  CourseEnrollment/UserCertification it has two different
                  people on it: ownerId (the rep working it, resolved via
                  SequenceEnrollmentService#resolveOwner, same null-defaults-
                  to-caller/ORGANIZATION-scope-to-assign-elsewhere rule
                  TicketService#resolveOwner already applies) and targetId
                  (the Lead or Contact being worked, validated against
                  LeadRepository/ContactRepository - never the enrollment's
                  authorization subject, since a rep working someone else's
                  lead through a sequence doesn't imply visibility into that
                  lead's other data). SequenceEnrollmentService#advance
                  increments currentStepIndex and auto-transitions to
                  COMPLETED once it walks off the end of the sequence's real
                  step list - "one past the last step" and "done" are the
                  same state, so there's no separate "mark complete" call,
                  the same way CourseEnrollmentService re-derives status from
                  real state rather than trusting the request.
  booking/        meeting scheduler ("book time with me" links) - V33, the third module
                  this session that's real new functional surface area, and the first
                  one that actively drives another module's service rather than just
                  reading its data. BookingLink mirrors ticket/'s owner-scoped shape
                  exactly (full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, in
                  RoleService#isCoreCrmResource). BookingSlot is a real FK'd child row
                  (on delete cascade), no permission of its own, managed through
                  BookingLinkService's addSlot/removeSlot the same way QuoteService
                  manages line items - except a slot also carries a real state machine
                  (OPEN -> BOOKED -> [terminal] / OPEN -> CANCELLED). BookingSlot#endAt
                  is computed once from the link's durationMinutes at slot-creation time
                  and stored, never recomputed if durationMinutes later changes - the
                  same "snapshot, don't drift" reasoning UserCertification#expiresAt and
                  CommissionRecord's frozen columns already document. BookingLinkService#
                  book/#cancel call straight into CalendarEventService#create/#delete to
                  create/soft-delete a real CalendarEvent rather than duplicating any
                  calendar logic here - see BookingLinkService's javadoc for the resulting
                  cross-module permission interaction (a TEAM-scoped manager can book a
                  report's link but the resulting CalendarEvent still needs its own
                  CALENDAR_EVENT:CREATE:ORGANIZATION grant to be assigned to that report,
                  since CalendarEventService#resolveOwner doesn't know or care that the
                  call came from inside another service). uq_booking_links_org_slug and
                  uq_booking_slots_link_start (V33) are real DB constraints backing the
                  slug-uniqueness and no-double-booking checks, not just in-memory ones.
  macro/          ticket macros (canned responses) - V34, the fourth module this session.
                  Macro mirrors product/'s no-OWN catalog shape (TEAM/DEPARTMENT/ORGANIZATION
                  only, no ScopeAuthorizationService call for the catalog's own CRUD).
                  MacroService#apply is the interesting part, and it's a THIRD distinct
                  cross-module-mutation pattern alongside the two that already existed in this
                  codebase: (1) TerritoryAssignmentListener/LeadScoringEngine inject a foreign
                  Repository directly and save() the foreign entity themselves, since they're
                  system-triggered @TransactionalEventListeners with no user permission to
                  check in the first place; (2) BookingLinkService#book/#cancel (V33) call a
                  foreign module's Service method directly. #apply follows (2), not a new
                  pattern - it calls straight through TicketService#update/#updateStatus,
                  deliberately NOT re-implementing Ticket's own OWN/TEAM/DEPARTMENT/
                  ORGANIZATION authorization against MACRO's permission instead, since a rep
                  who can merely read the macro catalog must not be able to mutate a ticket
                  they can't otherwise touch. See MacroService's javadoc for the full
                  reasoning. An inactive macro can be read but not applied - active/inactive
                  gating the same way Course/Sequence draw the line between "in the catalog"
                  and "in listActive."
  contract/       contracts (the ongoing legal/subscription relationship with a customer,
                  tracked after a deal closes) - V35, the fifth module this session and the
                  first added after the "keep going with more modules" checkpoint. Fills a
                  real, previously-missing gap: Quote (product/) is a pre-close proposal,
                  Order/Invoice (order/) are point-in-time transactions, neither tracks what
                  was actually agreed to or for how long. Contract mirrors ticket/'s owner-
                  scoped shape exactly (full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, in
                  RoleService#isCoreCrmResource) but with the more restrained CREATE/READ/
                  UPDATE/DELETE action set booking_links/course_enrollments/
                  sequence_enrollments already use (no EXPORT/IMPORT/ASSIGN). accountId is
                  required, opportunityId is nullable (a renewal contract has no opportunity
                  behind it) - both real FKs, same nullable-optional-link shape
                  tickets.contactId established. Status is a free (non-linear) state machine,
                  same restraint tickets.status takes (see V14's migration comment) -
                  reopening a terminated contract is a legitimate correction. signedAt is
                  stamped the first time status moves to ACTIVE and never overwritten
                  afterward, regardless of how many times the contract later moves through
                  other statuses - "when was this originally signed" shouldn't change.
                  contractNumber is unique per organization (uq_contracts_org_number, V35),
                  the same per-tenant-not-global uniqueness shape booking_links.slug uses.
  clientgoal/     client goals (a coach/trainer-defined, freeform measurable objective -
                  weight, strength, endurance, or a custom metric - tracked against one
                  Contact over time) - V36, the sixth module this session and the first
                  to lean into what this platform's own name ("ai-trainer-crm") actually
                  implies rather than staying purely generic-CRM. Distinct from everything
                  else in the schema: course_enrollments (V31) track progress THROUGH a
                  specific training course's content, sales_goals (V25) track an internal
                  rep's own quota (never a customer's), contracts (V35) track legal/
                  subscription terms. Owner-scoped CRM-resource pattern, mirrors contracts/
                  tickets exactly (full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, in
                  RoleService#isCoreCrmResource, the same restrained CREATE/READ/UPDATE/
                  DELETE action set contracts/booking_links use). ownerId is the coach/
                  trainer; contactId (a real, non-nullable FK) is the client the goal is
                  FOR - never the authorization subject, the same "owner and target are
                  different people" split sequence_enrollments established. Status is a
                  free (non-linear) state machine like contracts.status; achievedAt is
                  stamped the first time status moves to ACHIEVED and never overwritten
                  afterward, the same snapshot rule contracts.signedAt already documents.
  trainingsession/ training sessions (the post-session record of what actually happened in a
                  coaching session) - V37, the seventh module this session and the second
                  (after clientgoal, V36) to lean into what this platform's own name
                  ("ai-trainer-crm") implies. Deliberately distinct from booking/'s
                  BookingLink/BookingSlot (the PRE-session scheduling mechanism - "book time
                  with me") and from clientgoal's ClientGoal (the long-term target this
                  session is one unit of work toward - no FK between the two tables, purely
                  a reporting-time join on contactId). Owner-scoped CRM-resource pattern,
                  mirrors clientgoal/contract exactly (full OWN/TEAM/DEPARTMENT/ORGANIZATION
                  ladder, in RoleService#isCoreCrmResource, the same restrained CREATE/READ/
                  UPDATE/DELETE action set). contactId is a required real FK (the client,
                  never the authorization subject); bookingSlotId is an optional cross-
                  reference to the BookingSlot this session originated from, if any - since
                  BookingSlot itself carries no organizationId column (only bookingLinkId,
                  see booking/'s own V33 shape), TrainingSessionService#assertBookingSlotInOrganization
                  joins through BookingLinkRepository to check tenancy before accepting it.
                  status is a free (non-linear) state machine like tickets/contracts/
                  client_goals - a NO_SHOW logged in error can be corrected back to SCHEDULED.
  exercise/       exercise (movement) library - V38, the eighth module this session and the
                  third (after clientgoal V36, trainingsession V37) to lean into what this
                  platform's own name ("ai-trainer-crm") implies. Catalog-resource pattern,
                  mirrors course/product exactly (no ownerId, EXERCISE seeded at TEAM/
                  DEPARTMENT/ORGANIZATION only, no ScopeAuthorizationService call in
                  ExerciseService). Distinct from course/certification (structured, enrollable
                  curriculum content with progress/pass-fail) and trainingsession (the
                  post-session log of what happened) - Exercise is the atomic named-move
                  building block ("Barbell Back Squat") a coach references when planning.
                  Deliberately does NOT get referenced by training_sessions in this pass - see
                  V38's migration comment for why a real per-session exercise/sets/reps table
                  is a separate, bigger feature this pass doesn't take on, and
                  training_sessions.focus_area's free text already covers this module's
                  actual need. name is unique per organization, case-insensitively
                  (uq_exercises_org_name, V38) - same per-tenant uniqueness shape
                  uq_contracts_org_number (V35) establishes.

                  training_session_exercises (V39, the ninth module this session) is the
                  connective tissue between trainingsession and exercise that both V37's and
                  V38's migration comments flagged as deliberately unbuilt - which specific
                  exercises, with which sets/reps/weight, were actually performed in a given
                  session. Child-entity-of-parent pattern, mirrors quote's QuoteLineItem/
                  sequence's SequenceStep exactly: a real FK to training_sessions with on
                  delete cascade, no permission of its own - add/update/remove is gated
                  entirely on the parent session's own TRAINING_SESSION:UPDATE permission via
                  TrainingSessionService, embedded in TrainingSessionDto via
                  TrainingSessionDto.from(session, exercises) the same way QuoteDto embeds
                  line items. exerciseId is nullable and carries no cascade, mirroring
                  quote_line_items.product_id - a coach can log a movement that isn't in the
                  catalog yet. exerciseName is a snapshot stamped once at creation, never
                  resynced if the catalog entry is later renamed - the same restraint
                  booking_slots.end_at/contracts.signed_at/client_goals.achieved_at already
                  established, applied here to a child-entity field. No status column - like
                  quote_line_items/sequence_steps, this is an append-only log row, not a
                  resource that moves through states.

  nutritionplan/  nutrition plans (V40, the tenth module this session) - a coach-authored
                  dietary prescription (daily calorie target, macro targets, freeform guidance)
                  for one Contact over a date range. Distinct from clientgoal (a long-term
                  measurable OUTCOME), trainingsession/trainingsessionexercise (completed
                  WORKOUTS), and exercise (the movement catalog) - nothing else covers the
                  dietary/macro-guidance side of coaching. Owner-scoped CRM-resource pattern,
                  mirrors clientgoal/contract exactly (full OWN/TEAM/DEPARTMENT/ORGANIZATION
                  ladder, in RoleService#isCoreCrmResource, the same restrained CREATE/READ/
                  UPDATE/DELETE action set). contactId is a required real FK (the client, never
                  the authorization subject); status is a free (non-linear) state machine like
                  contracts/client_goals/training_sessions. startDate/endDate are both nullable
                  (an ongoing plan may carry neither) and only cross-validated when both are
                  present, unlike Contract's always-required pair.

  bodymeasurement/ body measurements (V41, the eleventh module this session) - a periodic,
                  point-in-time snapshot of a client's physical stats (weight, body fat %, chest/
                  waist/hips circumference) recorded by a coach against one Contact. Distinct from
                  clientgoal (a single named OBJECTIVE row with startValue/targetValue/
                  currentValue, no history of every reading) - bodymeasurement is a real time
                  series, many rows per contact, one per check-in, append-only. Owner-scoped
                  CRM-resource pattern, mirrors nutritionplan/clientgoal exactly (full OWN/TEAM/
                  DEPARTMENT/ORGANIZATION ladder, in RoleService#isCoreCrmResource, the same
                  restrained CREATE/READ/UPDATE/DELETE action set). contactId is a required real
                  FK (the client measured, never the authorization subject). Deliberately has NO
                  status field, unlike every other owner-scoped module this session - a
                  measurement check-in is a log entry, not a resource with a lifecycle to
                  transition through. measuredAt is a separate date field from createdAt (a coach
                  may backfill an entry). No photo/image column - attachment.related_to_type
                  already includes CONTACT, so progress-photo check-ins ride on the existing
                  attachment module rather than duplicating file-storage plumbing.
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
`leadscoring` (`LeadScoringRuleController` specifically - see below),
`salesgoals` (`SalesGoalController` specifically - see below), and
Team (in `organization/`) are a third kind: platform
administration, gated entirely by `API_KEY:*:ORGANIZATION` /
`INTEGRATION:*:ORGANIZATION` / `CUSTOM_FIELD:*:ORGANIZATION` /
`CUSTOM_OBJECT:*:ORGANIZATION` / `SLA_POLICY:*:ORGANIZATION` /
`TERRITORY_RULE:*:ORGANIZATION` / `LEAD_SCORING_RULE:*:ORGANIZATION` /
`SALES_GOAL:*:ORGANIZATION` / `TEAM:*:ORGANIZATION` (no OWN/TEAM/
DEPARTMENT variant exists for any of these nine resources - a bit of an
irony for `TEAM` specifically, whose entire purpose is backing other
resources' TEAM/DEPARTMENT scope, but managing *teams themselves* is
still an org-wide admin action, same as managing users or roles), with no
per-record ownership concept at all - see `ApiKeyController`'s,
`WebhookSubscriptionController`'s, `CustomFieldController`'s/
`CustomObjectController`'s, `SlaPolicyController`'s,
`TerritoryRuleController`'s, `LeadScoringRuleController`'s,
`SalesGoalController`'s, and `TeamController`'s
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
the auto-assignment those rules cause. `leadscoring`'s CRUD service
(`LeadScoringRuleService`) is the same pattern again, one level further:
its actual scoring behavior lives entirely in `LeadScoringEngine`, which -
unlike `TerritoryAssignmentListener` - reacts to both `RecordCreated` AND
`RecordUpdated`, so `LEAD_SCORING_RULE`'s permission gates only *defining*
rules, never the (re)scoring those rules cause on every Lead write. Note `CustomFieldController#/values`
deliberately gates reading/writing a *value on a standard entity's record*
(e.g. an Account) on `CUSTOM_FIELD:*:ORGANIZATION` rather than
`ACCOUNT:UPDATE` - a documented simplification, not an oversight. By
contrast, `dashboard`'s own read path (`DashboardService#getData`) is a
correct use of an *existing* permission, not a shortcut: rendering a
dashboard's widget data delegates straight into `ReportService`, which
enforces REPORT:READ's own OWN/TEAM/ORGANIZATION scope internally - so
viewing a dashboard's numbers requires the caller to hold both DASHBOARD:
READ (the shell) and some level of REPORT:READ (each widget's data), not a
single all-encompassing DASHBOARD permission. `forecast` goes one step
further than either of those: unlike `dashboard` (which has its own
DASHBOARD permission layered on top of REPORT:READ) or `sla`'s
`TicketSlaController` (which checks TICKET:READ inline, with no
`@PreAuthorize` of its own), `PipelineSnapshotController` has real
`@PreAuthorize` annotations, and they name REPORT:READ directly - there is
no FORECAST resource in the permission catalog at all, because a pipeline
snapshot is exactly the same data `report`'s live pipeline-by-stage query
already gates on REPORT:READ, just persisted. Inventing a second
permission for the persisted copy of the same numbers would be pure
duplication, not a real access-control distinction.
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
`dedupe` (in `dedupe/`) is a sixth kind, and the inverse of `notification`'s:
where `notification` skips the permission catalog because a notification's
scope can only ever mean "yourself" (narrower than any existing permission
could express), `dedupe` skips it because a `DuplicateMatch` needs no scope
of its own at all - it names two existing LEAD/CONTACT/ACCOUNT records, and
those resources' own READ/UPDATE permissions, checked against *both* named
records, are already exactly the right gate. Adding `DUPLICATE_MATCH` would
not add precision, it would remove it - see `DuplicateMatchService`'s
javadoc and V23's migration comment.
`salesgoals` (in `salesgoals/`) is a seventh kind, and the first module this
session to combine two earlier patterns rather than introduce a new one:
`SalesGoalController`'s five CRUD-shaped endpoints are a plain instance of
the third kind (`SALES_GOAL:*:ORGANIZATION`, no `ScopeAuthorizationService`
call), but `GET /sales-goals/mine` sitting right alongside them is a plain
instance of the fourth kind (`notification`'s self-scoped, no-permission-
check shape) - `SalesGoalService#myGoals` just filters on `principal.getId()`
and their current `teamId`, exactly like `NotificationService` filters on
`recipientUserId`. Nothing new was invented to let a rep see their own quota
without an admin permission; the module just reuses the one pattern already
built for "a user's own thing" and layers admin CRUD on top for everyone
else's. See V25's migration comment and `SalesGoalService`'s javadoc.

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
