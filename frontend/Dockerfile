# Multi-stage build: node_modules and the Vite/TypeScript toolchain never ship -
# only the static dist/ output gets copied into the nginx stage.

FROM node:22-alpine AS build
WORKDIR /app

# npm ci (not install) for a build stage: it installs exactly what
# package-lock.json pins, fails instead of silently updating the lockfile if
# it's out of sync, and is faster - all things you want in an automated build.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# No VITE_API_BASE_URL is set here on purpose: the build stays relative-path
# ("/api/v1/...") and nginx.conf proxies /api/ to the backend container, the
# same pattern the Vite dev-server proxy uses locally (see vite.config.ts) -
# one fewer thing to configure, and the browser only ever talks to one origin.
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
