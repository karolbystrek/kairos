# Kairos

## Overview
Virtual pager system for restaurants. Customer scans a QR code assigned to their order to open a web app that tracks order status in real-time (WebSocket). Staff manage the order queue via an admin panel. External POS systems can integrate via a REST/webhook API.

Full problem description: `docs/PROBLEM_DESCRIPTION.md`.
Full architecture and requirements: `docs/REQUIREMENTS.md`.

> **Development prerequisite:** Read `docs/REQUIREMENTS.md` in full before planning, implementing, reviewing, refactoring, or otherwise changing project code, database migrations, APIs, security, infrastructure, or tests. Treat it as the source of truth for system behavior and architecture throughout the task, not only during initial orientation.

## Tech Stack
* **customer-app** (`apps/customer-app`): Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI 3, Zod, SWR for client-side server state.
* **panel-app** (`apps/panel-app`): Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI 3, Zod, SWR where client-side synchronization is useful.
* **api** (`apps/api`): Java 25, Spring Boot 4, Spring Security, Spring WebSocket/STOMP.
* **Database:** PostgreSQL. Shared database/shared schema, tenant isolation via Row Level Security using direct or relationship-derived ownership as defined in `docs/REQUIREMENTS.md`.
* **Cache & real-time:** Redis (WebSocket session store, pub/sub for notifications).
* **Infra:** Docker Compose, Caddy (reverse proxy/TLS).

## Architecture Boundaries
* Keep `customer-app`, `panel-app`, and `api` independently deployable.
* Spring Boot is the system of record and the sole owner of business rules, authentication, authorization, tenant isolation, persistence, WebSockets, POS integration, webhooks, and outbox processing.
* Browser-facing `/api`, WebSocket, and OAuth paths are exposed through Caddy on the relevant frontend origin. Keep the dedicated API origin for external POS integrations.
* Keep REST as the boundary between the frontends, external POS integrations, and the API. The first walking vertical slice uses small handwritten TypeScript types and native `fetch`; formal API documentation and client generation are deferred until the contract needs to support external integrations.
* Use STOMP over WebSocket for order events. Validate incoming WebSocket payloads with Zod before using them in the customer app.
* Use native `fetch` inside the handwritten REST request modules. Use SWR in Client Components to manage REST-backed server state, including caching, request deduplication, mutations, focus revalidation, and reconnect revalidation. Do not add Next.js Server Actions or proxy route handlers as an API layer in front of Spring.

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
* **Always use HeroUI** (`@heroui/react`) for UI components in `apps/customer-app` and `apps/panel-app`.
* Use Zod for frontend-owned input and event validation. During the walking vertical slice, keep REST request functions and response types small and handwritten in each frontend.
* Do not use React effects to orchestrate routine REST request state. Reserve effects for synchronization with external systems, such as error reporting or future WebSocket/browser API subscriptions. Keep the official `eslint-plugin-react-hooks` recommended rules enabled, including exhaustive dependency validation; use local state hooks normally for frontend-owned interaction state.
* Organize backend code by business feature. Within each feature, separate `api`, `application`, `domain`, and `infrastructure` packages; keep transport models and HTTP concerns at the API boundary and business behavior in the domain.
* Enforce security and tenant access in the API. Frontend redirects and hidden controls are user-experience features, not authorization controls.
* Model each internally registered panel account as owned directly by one tenant. Tenant administrators have tenant-wide access and may provision location managers or operators. Managers and operators have at most one location assignment; managers may provision only operators for their own location, while operators cannot provision accounts. Treat location operator accounts as device-oriented and create a separate account for each panel device that needs independent credentials or revocation. Do not add self-registration.
* Keep production migrations free of seeded tenants, locations, accounts, credentials, or other environment-specific records. Tests create isolated fixtures and roll them back.
* Serialize concurrent transitions of the same order with a database row lock before validating the current state. Store each accepted resulting state once in append-only history rather than duplicating its preceding state.
* Classify known transition initiators generally as a user, external integration, or system action and record the corresponding identity where one exists. Never trust an initiator identity supplied by an unauthenticated client.
* **Always use Conventional Commits** for commit messages (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`, `test:`, etc.).
* Each app (`customer-app`, `panel-app`, `api`) is independent: separate dependency manifests, separate Dockerfiles.

## Current Development Stage
The walking vertical slice is local-only and deliberately has no staff authentication, WebSocket delivery, OpenAPI endpoint, or generated REST client yet. It supports staff order creation and transitions, QR generation, and anonymous REST tracking through SWR-managed client state backed by handwritten native `fetch` requests. The unauthenticated order-management API is a temporary delivery stage, not authorization policy; add Spring Security authentication, tenant/location enforcement, CSRF protection, and RLS before deployment. Production migrations must still create an empty, deployable schema rather than demo data.

## Documentation Synchronization
* Do not leave accepted project decisions only in the conversation. When discussion with the user changes or clarifies architecture, requirements, scope, security, data ownership, technology choices, or development conventions, update the relevant documentation in the same task.
* Update `docs/REQUIREMENTS.md` for product behavior, architecture, security, persistence concepts, integration contracts, and acceptance criteria.
* Update `AGENTS.md` for repository-wide technology choices, boundaries, workflows, and implementation conventions agents must follow.
* Keep documentation and code consistent. When a code change alters documented behavior or structure, update the affected documentation alongside the code; when a documentation decision affects existing code, identify and reconcile the mismatch rather than silently ignoring it.

## Agent Development Workflow
* Before changing files, inspect `git status --short` and run `docker compose ps`. Assume an already-running stack belongs to the user and was started with `docker compose up --build --watch`; do not start a duplicate Watch process.
* If the stack is not running and runtime verification is needed, start it with `docker compose up --build --watch` and keep that process active for the task.
* Compose Watch requires Docker Compose 2.32.2 or newer. Frontend source is synchronized into development containers for Next.js Fast Refresh. Backend source is synchronized, compiled with the Maven wrapper inside the container, and then reloaded by Spring Boot DevTools.
* If a change is not reflected in a running service, inspect bounded service logs first. As a fallback, rebuild and recreate only affected services with `docker compose up -d --build --no-deps <service...>`.
* Treat the running environment as user-owned. Never stop the complete stack, run `docker compose down`, delete volumes, reset PostgreSQL, or prune Docker state unless the user explicitly authorizes it.
* Use repository-owned command interfaces instead of invoking installed binaries directly:
  * From the repository root, validate a frontend with `npm --prefix apps/customer-app run check` or `npm --prefix apps/panel-app run check`.
  * Use `npm run` or `npm --prefix` for frontend tooling. Do not invoke `node_modules/.bin`, use `npx` for an installed project tool, or run raw ESLint or TypeScript commands.
  * Run backend Maven commands through `apps/api/mvnw` (or `./mvnw` from `apps/api`), never through a system `mvn` executable.
  * Add or update frontend dependencies with `npm install` from the affected app so its `package-lock.json` remains synchronized. Do not edit lockfiles manually.
* `npm run lint` and `npm run check` are read-only validation commands. Use `npm run lint:fix` only as an intentional edit, then review the resulting diff.
* Validate the affected scope before handoff:
  * Frontend change: run that app's `check` script.
  * Backend change: run `./mvnw test` from `apps/api`.
  * Shared REST contract change: run backend tests and `check` in every consuming frontend.
  * Always run `git diff --check`.
  * Reserve production builds for dependency, build configuration, or Dockerfile changes, explicit release verification, or when the task specifically requires them.
* A changed Flyway migration can be rejected by a persistent local database because its recorded checksum no longer matches. Report the failure and ask before any database-volume reset; never erase the volume automatically.
* In the final report, distinguish checks that passed, failed, were blocked by the environment, or were not run. Do not describe stale containers or interrupted commands as successfully verified.

## Local Development
```bash
cp .env.example .env
docker compose up --build --watch
```
* Customer app: https://app.localhost
* Panel app: https://panel.localhost
* API: https://api.localhost
