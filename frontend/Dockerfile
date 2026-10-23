# syntax=docker/dockerfile:1

# Build stage: type-check and bundle with Vite.
FROM node:24-alpine AS build
WORKDIR /app

# Dependencies install before the sources are copied, so a source-only change
# reuses the installed node_modules layer.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Runtime stage: plain nginx serving the bundle and proxying /api to the backend.
FROM nginx:1.28-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --spider -q http://127.0.0.1:80/healthz || exit 1
