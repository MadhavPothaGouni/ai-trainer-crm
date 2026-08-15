# AI-Trainer CRM

A full CRM and client-management platform for independent trainers and
coaching businesses, built as a Spring Boot modular monolith with a
separate React SPA. It covers the standard CRM core (accounts, contacts,
opportunities, leads), sales and support tooling, marketing, automation,
compliance, and platform administration, alongside a training-specific
layer (courses/certifications, client goals, training sessions, exercises,
nutrition plans, body measurements, and booking links) — see
[Features](#features) for the full list.

## Architecture

```
┌─────────────────┐        ┌──────────────────────────────────────┐
│  React frontend  │  /api  │        crm-platform (Spring Boot)      │
│  (nginx / Vite)  │───────▶│  50+ feature modules: CRM, sales,      │
│                  │        │  support, training, marketing,         │
│                  │        │  automation, admin  (one deployable)   │
└─────────────────┘        └───────────┬──────────┬──────────┬──────┘
                                        │          │          │
                                   PostgreSQL    Redis    RabbitMQ
```

**Backend** — Spring Boot 3.2.5 / Java 17, organized as a *modular monolith*:
one deployable unit, one database, but code is split into feature modules
(`auth`, `organization`, `user`, `role`, `account`, `opportunity`, `ticket`,
`workflow`, ...) with clear boundaries, rather than either a tangled single
package or a premature microservices split. Postgres is the system of
record, Redis backs caching (including role-permission resolution for
auth), and RabbitMQ/Quartz handle async and scheduled work.

**Frontend** — React 19 + TypeScript + Vite + Tailwind CSS v4, a pure JSON
API client (no server-side rendering, no session cookie — JWT access token +
rotating opaque refresh token, both returned in the response body).

### Auth & RBAC model

- **Auth**: stateless JWT access tokens (15 min default) + opaque, rotating
  refresh tokens (30 days default) with server-side reuse detection — presenting
  an already-rotated-away refresh token revokes every session for that user,
  not just the one request. The access token carries only the caller's role
  ids; the permissions each role grants are resolved from a cached
  server-side lookup rather than embedded in the token itself, so the
  token stays small no matter how large the permission catalog grows.
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

## Features

Every module below is built end to end: backend entity/repository/service/
controller/DTO with unit and integration tests, and a matching frontend
page (a few compose into an existing page instead of getting their own —
noted where that's the case). See `backend/crm-platform/README.md`'s
module layout for design rationale on any individual module.

**Core CRM** — Accounts, Contacts, Opportunities (sales pipeline), and
Leads (with conversion into an Account/Contact/Opportunity), plus an
Activity log (calls, emails, meetings, tasks, notes) attachable to any CRM
record. Every list view enforces record-level OWN/TEAM/DEPARTMENT/
ORGANIZATION scope authorization.

**Sales & commerce** — a Product catalog; Quotes (priced proposals against
an Opportunity); Orders (converted from a Quote, DRAFT → CONFIRMED →
FULFILLED); Invoices (generated from an Order, DRAFT → SENT → PAID, driven
automatically by recorded Payments rather than settable directly);
Contracts; Membership Plans and Memberships (recurring client billing,
price/credits snapshotted at signup so later plan-price changes don't
retroactively affect existing members); a Commission engine that
auto-calculates rep commission on closed-won deals; Sales Goals (rep/team
quotas); Forecasting (daily pipeline snapshots for trend reporting); a
Referral Program (clients referring people they know, worked through
PENDING → CONTACTED → CONVERTED, with an optional reward issued once and
never re-issued); and a Vendor catalog with Purchase Orders (DRAFT →
ORDERED → RECEIVED, received-at timestamp stamped once).

**Support & service** — Support Tickets (free, non-linear status
transitions, since reopening a resolved ticket is normal); SLA policies
with automatic escalation; canned-response Macros; and a Knowledge Base of
help-center articles (slugged, tagged, DRAFT → PUBLISHED → ARCHIVED, with
view counts).

**Training & fitness coaching** — Course/Certification management with
enrollment and credential awarding; Client Goals (coach-defined measurable
objectives); Training Sessions with per-session exercises logged; an
Exercise library; Nutrition Plans; Body Measurement check-ins; Booking
Links for client self-scheduling; Group Classes (a class-type catalog,
scheduled Class Sessions, and a capacity-enforced attendance roster —
distinct from the 1:1 Training Session/Booking Link); an Equipment
inventory with Maintenance Logs tracking service history per asset; Staff
Shift Scheduling (recurring shift templates plus actual scheduled shifts,
with clock-in/out timestamps stamped once per shift); and Client
Documents (waivers, medical clearances, photo releases, tracked
PENDING → SIGNED with a signed-at timestamp stamped once).

**Marketing & content** — Campaigns with member tracking (Leads/Contacts
through an engagement funnel) and per-status stats; Email Templates;
Sales Sequences (multi-step engagement cadences); logged Emails; and a
Calendar with per-attendee response tracking.

**Automation & rules** — Workflow automation (trigger-based follow-up
tasks on record create/update/delete, with run history and a manual "run
now"); Territory assignment rules (auto-routes new records to an owner);
Lead Scoring; Duplicate detection and merge; and a Territory Hierarchy
(Region) rollup for reporting.

**Analytics & personalization** — Reports (pipeline by stage, lead
conversion funnel, rep leaderboard, backed by real aggregation queries);
Dashboards (saved widget layouts pulling live from the same Reports
queries); and Saved Views (per-user filter/sort presets, embedded as a bar
on each list page rather than a standalone page).

**Compliance** — GDPR data-subject export/erase requests, and multi-step
Approval Workflows for Quotes/Orders/Opportunities.

**Platform & admin** — Organizations, Teams, and Users; full RBAC
(permission catalog × roles); Authentication (register/login/refresh/
password reset/email verification); API Keys for programmatic access;
outbound Webhooks (HMAC-signed HTTP callbacks); an in-app Notification
inbox; file Attachments (pluggable storage behind a `FileStorageService`
interface); bulk CSV Import/Export; and Custom Objects/Fields for
platform extensibility (EAV-backed, validated against each field's
declared type).

Cross-cutting: a full frontend test suite (Vitest + React Testing
Library), Docker Compose + CI for both halves, and a production deploy
pipeline (versioned GHCR image publishing + GitHub Releases on tag push —
see [Deploying to production](#deploying-to-production)).

## Author

**Potha Gouni Madhav**
Email: pothagounimadhav@gmail.com
