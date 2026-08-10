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

# Frontend: type-check + production build (no test suite yet - see Roadmap)
cd frontend && npm run lint && npm run build
```

## CI/CD

Three independent GitHub Actions workflows, each scoped to the paths it
actually needs to run for (a frontend-only change doesn't trigger a backend
build, and vice versa):

| Workflow | Triggers on | Does |
|---|---|---|
| `backend-ci.yml` | `backend/crm-platform/**` | `mvn verify` (unit + integration tests against a real Postgres), uploads surefire + JaCoCo reports |
| `frontend-ci.yml` | `frontend/**` | `npm run lint` + `npm run build`, uploads the `dist/` artifact |
| `docker-build.yml` | either `Dockerfile`'s directory, or `docker-compose.yml` | Builds both container images (no push — no registry configured yet) to catch a broken Dockerfile before deploy time |

## Repository layout

```
backend/crm-platform/   Spring Boot backend (see its own README.md)
frontend/               React SPA (see its own README.md)
docker-compose.yml       Full local stack: postgres, redis, rabbitmq, backend, frontend
.env.example             Docker Compose env vars (copy to .env)
.github/workflows/       backend-ci.yml, frontend-ci.yml, docker-build.yml
```

## Roadmap

Built so far: backend scaffold, auth module (register/login/refresh/password
reset/email verification), organizations/users/RBAC modules, the CRM domain
itself (Account/Contact/Opportunity/Lead, including lead conversion and
record-level OWN/TEAM/DEPARTMENT/ORGANIZATION scope authorization), React
auth scaffold (login/register/forgot/reset/verify-email pages + protected
routing), a CRM workspace UI (list/create/detail pages for accounts,
contacts, opportunities, and leads, including opportunity stage transitions
and lead conversion from the UI), Docker Compose + CI for both halves.

Not yet built:
- Frontend UI for team/role management (the typed API client already exists
  in `frontend/src/api/{users,roles}.ts` — no pages consume it yet)
- Frontend test suite
- Production deploy pipeline (image push + release)
