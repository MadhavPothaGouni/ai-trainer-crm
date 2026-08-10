# AI-Trainer CRM — frontend

React + TypeScript + Vite + Tailwind CSS v4 client for the `crm-platform` backend.

## Stack

- **React 19** + **TypeScript**, built with **Vite**
- **Tailwind CSS v4** (via `@tailwindcss/vite`, no separate config file needed)
- **react-router-dom** for routing, with `ProtectedRoute`/`PublicOnlyRoute` gates
- **react-hook-form** + **zod** for form state and client-side validation
- **axios** for HTTP, with a response interceptor that transparently refreshes an
  expired access token and retries the original request once

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
```

## Structure

```
src/
  api/            typed wrappers around each backend module (auth, users, organizations, roles)
  auth/           AuthContext/AuthProvider, useAuth hook, ProtectedRoute/PublicOnlyRoute, token storage
  components/
    layout/       AuthLayout (centered card, for /login etc.) and AppLayout (top bar + outlet)
    ui/           small shared primitives: Button, TextField, Alert
  lib/
    apiClient.ts  axios instance, auth header injection, 401 refresh-and-retry, ApiError
    validation.ts zod schemas mirroring the backend's request DTO constraints
    formErrors.ts maps a VALIDATION_FAILED response onto react-hook-form field errors
  pages/
    auth/         LoginPage, RegisterPage, ForgotPasswordPage, ResetPasswordPage, VerifyEmailPage
    DashboardPage.tsx  minimal authenticated landing page (org + account summary)
    NotFoundPage.tsx
  types/api.ts    TypeScript mirror of every backend DTO and response envelope
```

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

This is the auth scaffold only. Team management, role editing, and the actual CRM
workspace (leads/contacts/deals/etc.) aren't built yet — `DashboardPage` is a
placeholder that proves the login → protected route → API call chain works
end-to-end. The typed API client for users/roles (`src/api/users.ts`,
`src/api/roles.ts`) is already in place for whoever picks that up next.
