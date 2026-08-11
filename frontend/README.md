# AI-Trainer CRM — frontend

React + TypeScript + Vite + Tailwind CSS v4 client for the `crm-platform` backend.

## Stack

- **React 19** + **TypeScript**, built with **Vite**
- **Tailwind CSS v4** (via `@tailwindcss/vite`, no separate config file needed)
- **react-router-dom** for routing, with `ProtectedRoute`/`PublicOnlyRoute` gates
- **react-hook-form** + **zod** for form state and client-side validation
- **axios** for HTTP, with a response interceptor that transparently refreshes an
  expired access token and retries the original request once
- **Vitest** + **React Testing Library** for unit/component tests (jsdom environment)

## Getting started

```bash
npm install
npm run dev
```

The dev server runs on `http://localhost:5173` and proxies any `/api/*` request to
the backend (`http://localhost:8080` by default — see `server.proxy` in
`vite.config.ts`, override with `VITE_BACKEND_URL` if the backend runs elsewhere).
Make sure `crm-platform` is running locally first (see `../backend/crm-platform/README.md`
if present, or `docker-compose.yml` at the repo root).

For a production build, copy `.env.example` to `.env` and set `VITE_API_BASE_URL`
if the frontend will be served from a different origin than the backend.

```bash
npm run build    # type-checks (tsc -b) then builds to dist/
npm run preview  # serve the production build locally
npm run test     # Vitest, single run (CI mode)
```

## Structure

```
src/
  api/            typed wrappers around each backend module (auth, users, organizations,
                   roles, accounts, contacts, opportunities, leads, activities, products,
                   quotes, reports)
  auth/           AuthContext/AuthProvider, useAuth hook, ProtectedRoute/PublicOnlyRoute, token storage
  components/
    layout/       AuthLayout (centered card, for /login etc.) and AppLayout (top bar + nav + outlet)
    ui/           shared primitives: Button, TextField, TextArea, Select, Alert, Pagination
    roles/        PermissionPicker (permission-catalog checkbox grid, shared by role create/edit)
    activities/   ActivityTimeline - the calls/emails/meetings/tasks/notes log embedded
                   on each Account/Contact/Opportunity/Lead detail page
  lib/
    apiClient.ts  axios instance, auth header injection, 401 refresh-and-retry, ApiError
    validation.ts zod schemas mirroring the backend's request DTO constraints
    formErrors.ts maps a VALIDATION_FAILED response onto react-hook-form field errors
  pages/
    auth/         LoginPage, RegisterPage, ForgotPasswordPage, ResetPasswordPage, VerifyEmailPage
    accounts/, contacts/, opportunities/, leads/
                   list/create/detail pages for the CRM domain - opportunity detail
                   includes a stage-change control and its own embedded Quotes list,
                   lead detail includes the convert-lead flow, and every detail page
                   embeds an ActivityTimeline
    products/, quotes/
                   Product catalog pages, and Quote pages including an inline
                   line-item editor (add/edit/remove, with a product picker that
                   auto-fills description/unit price) and server-computed totals
    reports/        ReportsPage.tsx - pipeline-by-stage and lead-funnel bar
                   charts plus a rep leaderboard table, all plain CSS-width
                   bars (no charting library); only visible in nav to
                   OWNER/ADMIN, matching who holds REPORT:READ by default
    users/, roles/ team management (invite/list/roles/status) and role management
                   (built-in roles are read-only, custom roles get a permission picker)
    profile/       ProfilePage.tsx - update name/phone/timezone/locale, change password
                   (change-password signs the current session out too - the backend
                   revokes every refresh token for the account, not just other sessions)
    tasks/         MyTasksPage.tsx - every open activity assigned to the caller, across
                   every record, soonest due date first
    DashboardPage.tsx  authenticated landing page (org + account summary)
    NotFoundPage.tsx
  test/setup.ts   Vitest setup: jest-dom matchers + RTL cleanup after every test
  types/api.ts    TypeScript mirror of every backend DTO and response envelope
```

Tests live next to the file they cover (`Foo.tsx` / `Foo.test.tsx`), following
Vitest convention - run `npm run test` for a single CI-mode pass, or `npx vitest`
for watch mode during development.

## Auth flow

1. `AuthProvider` (wrapping the whole app in `main.tsx`) owns the current user and
   whether the initial "do we already have a session" check is still running.
2. Tokens live in `localStorage` (see `auth/tokenStorage.ts` for why — the backend
   returns them as plain JSON fields, not an httpOnly cookie, so there's nothing
   else to persist them).
3. Every request goes through `apiClient`, which attaches `Authorization: Bearer
   <accessToken>`. On a 401, it transparently calls `/api/v1/auth/refresh` once
   (de-duplicated across concurrent requests) and retries the original request; if
   the refresh itself fails, it clears tokens and notifies `AuthProvider`, which
   drops `user` back to `null` and `ProtectedRoute` redirects to `/login`.
4. `ProtectedRoute` guards the authenticated app shell; `PublicOnlyRoute` keeps a
   signed-in user from seeing the login/register forms again.

## What's not here yet

See the root README's Roadmap for the current state of the whole project. As of
this pass, the auth scaffold, the CRM workspace (accounts/contacts/opportunities/
leads, including lead conversion), team/role management, profile settings, the
Activity log (calls/emails/meetings/tasks/notes, plus a cross-record "My Tasks"
view), sales tooling (Product catalog + Quotes, including the inline
line-item editor and a per-opportunity Quotes list), and a Reports page
(pipeline-by-stage, lead funnel, rep leaderboard) are all built. What's still
missing on the frontend specifically: dedicated tests for the Product/Quote
pages (they have none yet, unlike every other page built so far - including
Reports), broader test coverage generally beyond the pages/components
covered so far, an avatar upload flow (`UserDto.avatarUrl` exists but
nothing sets it), and UI for the backend resources that don't have a
frontend yet - see the root README.
