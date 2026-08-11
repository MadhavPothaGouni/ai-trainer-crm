# AI-Trainer CRM

A CRM backend and frontend, built as a Spring Boot modular monolith with a
separate React SPA. This repo currently covers authentication and
organization/user/role administration (RBAC) end to end — see
[Roadmap](#roadmap) for what's not built yet.

## Architecture

```
┌─────────────────┐        ┌──────────────────────────────────────┐
│  React frontend  │  /api  │        crm-platform (Spring Boot)      │
│  (nginx / Vite)  │───────▶│  auth · organization · user · role ·   │
│                  │        │  audit · security  (one deployable)    │
└─────────────────┘        └───────────┬──────────┬──────────┬──────┘
                                        │          │          │
                                   PostgreSQL    Redis    RabbitMQ
```

**Backend** — Spring Boot 3.2.5 / Java 17, organized as a *modular monolith*:
one deployable unit, one database, but code is split into feature modules
(`auth`, `organization`, `user`, `role`, `audit`, ...) with clear boundaries,
rather than either a tangled single package or a premature microservices
split. Postgres is the system of record, Redis is for caching, RabbitMQ and
Quartz are in place for async/scheduled work as the feature set grows.

**Frontend** — React 19 + TypeScript + Vite + Tailwind CSS v4, a pure JSON
API client (no server-side rendering, no session cookie — JWT access token +
rotating opaque refresh token, both returned in the response body).

### Auth & RBAC model

- **Auth**: stateless JWT access tokens (15 min default) + opaque, rotating
  refresh tokens (30 days default) with server-side reuse detection — presenting
  an already-rotated-away refresh token revokes every session for that user,
  not just the one request.
- **RBAC**: `Permission` (`Resource` × `Action` × `Scope`, e.g.
  `USER:UPDATE:ORGANIZATION`) grouped into a `Role`, assigned to `User`s
  many-to-many. Every organization gets three system roles on creation —
  `OWNER`, `ADMIN`, `MEMBER` — which can't be edited or deleted; custom roles
  are built from the same permission catalog. An organization can never end up
  with zero `OWNER`s — enforced server-side on every role/status/removal change.
- **Multi-tenancy**: every org-scoped endpoint reads the caller's
  `organizationId` off their JWT, never a client-supplied value. A lookup
  for another tenant's data returns 404, not 403 — existence isn't leaked
  across tenants.

See `backend/crm-platform/README.md` for the module layout and
`frontend/README.md` for the frontend's structure and auth flow.

## Quick start (Docker Compose)

```bash
cp .env.example .env   # optional for local use - see below
docker compose up --build
```

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080` (Swagger UI at `/swagger-ui.html`)
- RabbitMQ management UI: `http://localhost:15672` (guest/guest by default)

Every service has a default that matches the backend's own
`application.yml` fallbacks, so this works with no `.env` file at all for
throwaway local use. Copy `.env.example` to `.env` and set a real
`JWT_SECRET` (and DB/RabbitMQ credentials, while you're at it) for anything
beyond that — the checked-in default is a placeholder, not a secret.

## Local development without Docker

Useful for backend hot-reload or frontend dev-server HMR, which the compose
setup's built images don't give you.

```bash
# Backing services only
docker compose up postgres redis rabbitmq

# Backend (separate terminal) - see backend/crm-platform/README.md for env vars
cd backend/crm-platform && mvn spring-boot:run

# Frontend (separate terminal) - proxies /api/* to localhost:8080 automatically
cd frontend && npm install && npm run dev
```

## Testing

```bash
# Backend: unit tests (Mockito) + integration tests (real Postgres via
# Testcontainers, full Spring Security filter chain via MockMvc)
cd backend/crm-platform && mvn verify

# Frontend: lint (oxlint) + unit/component tests (Vitest + React Testing
# Library, jsdom) + type-check and production build
cd frontend && npm run lint && npm run test && npm run build
```

## CI/CD

Three independent GitHub Actions workflows, each scoped to the paths it
actually needs to run for (a frontend-only change doesn't trigger a backend
build, and vice versa):

| Workflow | Triggers on | Does |
|---|---|---|
| `backend-ci.yml` | `backend/crm-platform/**` | `mvn verify` (unit + integration tests against a real Postgres), uploads surefire + JaCoCo reports |
| `frontend-ci.yml` | `frontend/**` | `npm run lint` + `npm run test` + `npm run build`, uploads the `dist/` artifact |
| `docker-build.yml` | either `Dockerfile`'s directory, or `docker-compose.yml` | Builds both container images (no push — this is pure validation, catching a broken Dockerfile before release time) |
| `release.yml` | a pushed `v*.*.*` tag (or manual dispatch) | Builds and pushes both images to GHCR, then creates a GitHub Release — see [Deploying to production](#deploying-to-production) |

## Deploying to production

`release.yml` publishes versioned images to GHCR whenever a `v*.*.*` tag is
pushed (or via manual dispatch against an existing tag):

```bash
git tag v1.0.0
git push origin v1.0.0
```

That builds `ghcr.io/<owner>/ai-trainer-crm-backend` and
`ai-trainer-crm-frontend`, each tagged both with the version and `latest`,
then creates a GitHub Release listing the published image tags. No secrets
to configure — it authenticates to GHCR with the workflow's own
`GITHUB_TOKEN`.

To run those images, use `docker-compose.prod.yml` instead of
`docker-compose.yml` — same service topology, but it pulls the published
images instead of building from source, and it doesn't publish the
Postgres/Redis/RabbitMQ ports to the host (only the app itself needs to be
reachable from outside the compose network):

```bash
cp .env.example .env
# fill in GHCR_NAMESPACE (your GitHub username/org), IMAGE_TAG, and real
# DB/RabbitMQ/JWT_SECRET/CORS_ALLOWED_ORIGINS values - .env.example has
# guidance for each
docker compose -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml` fails fast (via compose's `${VAR:?message}`
syntax) if a required variable is missing, rather than silently falling
back to a dev default the way `docker-compose.yml` does — there's no safe
placeholder for a production JWT secret or DB password.

## Repository layout

```
backend/crm-platform/    Spring Boot backend (see its own README.md)
frontend/                React SPA (see its own README.md)
docker-compose.yml       Full local stack: postgres, redis, rabbitmq, backend, frontend (built from source)
docker-compose.prod.yml  Production stack: same services, but pulls published GHCR images
.env.example             Env vars for both compose files (copy to .env)
.github/workflows/       backend-ci.yml, frontend-ci.yml, docker-build.yml, release.yml
```

## Roadmap

Built so far: backend scaffold, auth module (register/login/refresh/password
reset/email verification), organizations/users/RBAC modules, the CRM domain
itself (Account/Contact/Opportunity/Lead, including lead conversion and
record-level OWN/TEAM/DEPARTMENT/ORGANIZATION scope authorization), an
Activity module (calls/emails/meetings/tasks/notes logged against any CRM
record), sales tooling (a Product catalog and Quotes - priced proposals with
line items tied to an Opportunity, totals recomputed server-side), a
Reporting & analytics module (pipeline value by stage, lead conversion
funnel, and a per-rep leaderboard, backed by real aggregation queries over
Opportunity/Lead), a platform/integration layer (API key management for
programmatic auth, and webhook subscriptions - HMAC-signed HTTP callbacks
dispatched off the same domain events the audit log already consumes),
React auth scaffold (login/register/forgot/reset/verify-email pages +
protected routing), a CRM workspace UI (list/create/detail pages for
accounts, contacts, opportunities, and leads, including opportunity stage
transitions, lead conversion, a per-opportunity Quotes list, and a
per-record ActivityTimeline on every detail page), Products/Quotes pages
(including an inline line-item editor with a product picker), a Reports
page (pipeline-by-stage and lead-funnel charts plus the rep leaderboard),
API Keys and Webhooks pages (create/list/revoke a key with its raw value
shown exactly once; create/list/pause/delete a webhook subscription with
its signing secret and last-delivery status), a cross-record "My Tasks"
view, a team/role management UI (invite/list users, assign roles and
status, create/edit custom roles against the full permission catalog), a
"my profile" settings page (update name/phone/timezone/locale, change
password), a frontend test suite (Vitest + React Testing Library), Docker
Compose + CI for both halves, a production deploy pipeline (versioned
GHCR image publishing + GitHub Releases on tag push — see
[Deploying to production](#deploying-to-production)), and the order-to-cash
module end to end: Orders (optionally converted from a Quote, with a DRAFT
-> CONFIRMED -> FULFILLED lifecycle and a CANCELLED escape hatch), Invoices
(generated from an Order, DRAFT -> SENT -> PAID, PAID driven automatically
by recorded payments rather than settable directly), and Payments (recorded
against an Invoice, each one recomputing the invoice's amountPaid and
flipping its status once fully covered) on the backend - see
`backend/crm-platform/README.md`'s module layout for `order`/`invoice`/
`payment` - plus Orders/Invoices pages (an order's detail page has both a
"convert this quote" entry point and a "generate an invoice" card once
confirmed; an invoice's detail page locks its header/line items once
issued and grows a payment ledger with a record-payment form once sent),
and marketing/support tooling: Campaigns (with Campaign Members - a Lead or
a Contact tracked through an engagement funnel, plus a per-status stats
rollup) and the Knowledge Base (articles with auto-generated unique slugs,
tags, a DRAFT -> PUBLISHED -> ARCHIVED lifecycle, and a view counter) - see
`backend/crm-platform/README.md`'s module layout for `campaign`/
`knowledgearticle`. Campaigns and Knowledge Articles are also the first
resources in the whole platform with a real `:EXPORT` implementation (a CSV
download) rather than just a seeded-but-unbuilt permission. Most recently,
platform extensibility: Custom Objects (admin-defined generic entities -
a Name field plus whatever Custom Fields are attached) and Custom Fields
(attachable to a Custom Object or to a fixed set of standard entities -
Account/Contact/Lead/Opportunity/Campaign - never both, values stored as a
classic EAV table and parsed/validated against each field's declared type -
NUMBER/DATE/BOOLEAN/PICKLIST/TEXT/TEXT_AREA) - see
`backend/crm-platform/README.md`'s module layout for `customfield`. Most
recently, Workflow automation: a rule fires when a Lead/Contact/Account/
Opportunity is created/updated/deleted (matched against the same
`CrmAuditEvents` webhook delivery and the audit log already consume) and
creates a follow-up Activity task, assigned to either a configured user or
whoever owns the record that triggered it - with a full run history
(succeeded/failed, per fire) and a manual "run now" for testing a workflow
before switching it on. Unlike Campaign/Knowledge Article/Custom Field/
Custom Object, Workflow is owner-scoped (OWN/TEAM/ORGANIZATION) - see
`backend/crm-platform/README.md`'s module layout for `workflow`.

The permission catalog seeded in `V2__seed_permission_catalog.sql` now
covers exactly one resource with no module built on top of it -
`DASHBOARD` (a saved-report builder). That's deliberate: the RBAC model was
designed up front for the platform's eventual full shape, and it becomes an
`entity`/`repository`/`service`/`controller`/`dto` module following the
same pattern as `account`/`contact`/`opportunity`/`lead`/`activity`/
`product`/`quote`/`order`/`invoice`/`payment`/`campaign`/
`knowledgearticle`/`customfield`/`workflow`, plus a corresponding frontend
page, whenever it gets built. `report`, `apikey`, `webhook`, and
`customfield` are exceptions already built without a normal owner-scoped
entity of their own - see `backend/crm-platform/README.md`'s module layout
for why.

Not yet built, roughly in the order planned:
- A saved-report Dashboard builder
- Retry-with-backoff for webhook delivery (today it's a single attempt with
  a short timeout - see `WebhookDispatchListener`'s javadoc for the
  reasoning) and scoped/delegated API keys (today a key inherits its
  creator's full permission set rather than a chosen subset - see
  `ApiKeyService`'s javadoc)
- Broader frontend test coverage beyond the pages/components covered so far
  (the Product/Quote pages in particular have no dedicated tests yet), and
  the usual production-hardening items (rate limiting, observability/
  metrics, avatar upload) that weren't part of the original scope
