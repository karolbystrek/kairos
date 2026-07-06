# Kairos

## Overview
Virtual pager system for restaurants. Customer scans a QR code assigned to their order to open a web app that tracks order status in real-time (WebSocket). Staff manage the order queue via an admin panel. External POS systems can integrate via a REST/webhook API.

Full problem description: `docs/PROBLEM_DESCRIPTION.md`.
Full architecture and requirements: `docs/REQUIREMENTS.md`.

## Tech Stack
* **customer-app** (`apps/customer-app`): Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI.
* **panel-app** (`apps/panel-app`): Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI.
* **api** (`apps/api`): Java 25, Spring Boot 4
* **Database:** PostgreSQL. Shared database/shared schema, tenant isolation via Row Level Security on `tenant_id`.
* **Cache & real-time:** Redis (WebSocket session store, pub/sub for notifications).
* **Infra:** Docker Compose, Caddy (reverse proxy/TLS).

## Repository Structure
```
apps/
  customer-app/   Next.js client PWA (order tracking screen)
  panel-app/      Next.js staff admin panel (order queue, QR generation)
  api/            Spring Boot backend (REST API, WebSocket, POS webhooks)
docs/             Problem description and architecture/requirements docs
compose.yaml      Local dev orchestration (postgres, redis, api, apps, caddy)
Caddyfile         Reverse proxy config for local HTTPS
```

## Conventions
* **Always use HeroUI** (`@heroui/react`) for UI components in `apps/customer-app` and `apps/panel-app`. Do not introduce other component libraries.
* **Always use Conventional Commits** for commit messages (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`, `test:`, etc.).
* Each app (`customer-app`, `panel-app`, `api`) is independent: separate dependency manifests, separate Dockerfiles.

## Local Development
```bash
cp .env.example .env
docker compose up --build
```
* Customer app: https://app.localhost
* Panel app: https://panel.localhost
* API: https://api.localhost
