# Kairos

## Overview
Virtual pager system for restaurants. Customer scans a QR code assigned to their order to open a web app that tracks order status in real-time (WebSocket). Staff manage the order queue via an admin panel. External POS systems can integrate via a REST/webhook API.

Full problem description: `docs/PROBLEM_DESCRIPTION.md`.
Full architecture and requirements: `docs/REQUIREMENTS.md`.

> **Development prerequisite:** Read `docs/REQUIREMENTS.md` in full before planning, implementing, reviewing, refactoring, or otherwise changing project code, database migrations, APIs, security, infrastructure, or tests. Treat it as the source of truth for system behavior and architecture throughout the task, not only during initial orientation.

## Tech Stack
* **customer-app** (`apps/customer-app`): Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI 3, Zod.
* **panel-app** (`apps/panel-app`): Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI 3, Zod, SWR where client-side synchronization is useful.
* **api** (`apps/api`): Java 25, Spring Boot 4, Spring Security, Spring WebSocket/STOMP, springdoc-openapi.
* **Database:** PostgreSQL. Shared database/shared schema, tenant isolation via Row Level Security using direct or relationship-derived ownership as defined in `docs/REQUIREMENTS.md`.
* **Cache & real-time:** Redis (WebSocket session store, pub/sub for notifications).
* **Infra:** Docker Compose, Caddy (reverse proxy/TLS).

## Architecture Boundaries
* Keep `customer-app`, `panel-app`, and `api` independently deployable. Do not merge the two frontends.
* Spring Boot is the system of record and the sole owner of business rules, authentication, authorization, tenant isolation, persistence, WebSockets, POS integration, webhooks, and outbox processing.
* Browser-facing `/api`, WebSocket, and OAuth paths are exposed through Caddy on the relevant frontend origin. Keep the dedicated API origin for external POS integrations.
* The external and frontend API contract is REST documented with OpenAPI 3. Generate a `typescript-fetch` client independently for each frontend; do not maintain duplicate handwritten REST DTO types.
* Use STOMP over WebSocket for order events. Validate incoming WebSocket payloads with Zod before using them in the customer app.
* Do not introduce tRPC or a Next.js backend-for-frontend layer. Use native `fetch`, and use SWR only where polling, caching, or client-side revalidation provides value.
* Do not introduce NextAuth.js or another frontend authentication authority. Spring Security owns local credentials, OAuth2/OIDC login, JWT issuance, refresh-token rotation, and authorization.

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
* Use Zod for frontend-owned input and event validation. Treat the generated OpenAPI client as the source of REST request and response types.
* Enforce security and tenant access in the API. Frontend redirects and hidden controls are user-experience features, not authorization controls.
* **Always use Conventional Commits** for commit messages (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`, `test:`, etc.).
* Each app (`customer-app`, `panel-app`, `api`) is independent: separate dependency manifests, separate Dockerfiles.

## Documentation Synchronization
* Do not leave accepted project decisions only in the conversation. When discussion with the user changes or clarifies architecture, requirements, scope, security, data ownership, technology choices, or development conventions, update the relevant documentation in the same task.
* Update `docs/REQUIREMENTS.md` for product behavior, architecture, security, persistence concepts, integration contracts, and acceptance criteria.
* Update `AGENTS.md` for repository-wide technology choices, boundaries, workflows, and implementation conventions agents must follow.
* Keep documentation and code consistent. When a code change alters documented behavior or structure, update the affected documentation alongside the code; when a documentation decision affects existing code, identify and reconcile the mismatch rather than silently ignoring it.

## Local Development
```bash
cp .env.example .env
docker compose up --build
```
* Customer app: https://app.localhost
* Panel app: https://panel.localhost
* API: https://api.localhost
