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
* Organize backend code first by business feature and then by `api`, `application`, `domain`, and `infrastructure` layers. Add cohesive subpackages such as `model`, `exception`, `port`, `config`, `jwt`, `web`, or `persistence` when a layer contains distinct concerns; do not use broad catch-all packages or create one-class packages without a conceptual boundary.
* Configure the application-wide `/api` base path through `server.servlet.context-path` in `application.yaml`. Controller mappings declare only resource-relative paths and must not repeat `/api`; Actuator shares the same context path.
* Map application or domain projections to API response records through a static `from(...)` factory on the response type. Do not keep response-mapping helpers in controllers, and assign a service result to a clearly named local variable before passing it to `from(...)`.
* Use final dependency fields with Lombok `@RequiredArgsConstructor` for routine Spring constructor injection. Keep an explicit constructor only when it performs validation, derives additional state, or initializes a superclass.
* Use Lombok `@NonNull` for runtime-enforced null contracts on internal record components and domain factory or mutator parameters. Use Jakarta Bean Validation for validated API input, and do not write manual `Objects.requireNonNull(...)` guards.
* Prefer Spring Data derived query methods when a property path expresses the query. Put descriptive lock semantics before `By`, retain `@Lock` for pessimistic locking, and reserve manual `@Query` declarations for bulk updates or genuinely non-derivable queries.
* Use Lombok `@Slf4j` instead of declaring `Logger` and `LoggerFactory` fields manually.
* Use `var` for initialized local variables when the initializer makes the type evident. Keep an explicit type when inference is impossible or when an interface, generic contract, or numeric width materially improves understanding.
* Indent Java source and test code with four spaces. Do not use tab characters in Java files.
* Enforce security and tenant access in the API. Frontend redirects and hidden controls are user-experience features, not authorization controls.
* Model each panel account as owned directly by one tenant. Public tenant onboarding may create only the tenant's first administrator and must not create an authenticated session. All standalone accounts remain provisioned: tenant administrators have tenant-wide access and may provision location managers or operators; managers and operators have at most one location assignment; managers may provision only operators for their own location, while operators cannot provision accounts. Treat location operator accounts as device-oriented and create a separate account for each panel device that needs independent credentials or revocation. Do not add standalone account self-registration.
* Keep production migrations free of seeded tenants, locations, accounts, credentials, or other environment-specific records. Tests create isolated fixtures and roll them back.
* Serialize concurrent transitions of the same order with a database row lock before validating the current state. Store each accepted resulting state once in append-only history rather than duplicating its preceding state.
* Classify known transition initiators generally as a user, external integration, or system action and record the corresponding identity where one exists. Never trust an initiator identity supplied by an unauthenticated client.
* **Always use Conventional Commits** for commit messages (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`, `test:`, etc.).
* Each app (`customer-app`, `panel-app`, `api`) is independent: separate dependency manifests, separate Dockerfiles.

## Current Development Stage
The walking vertical slice is local-only. The Spring API contains provisioned local username/password accounts, signed access and rotating refresh credentials in secure cookies, CSRF protection, logout and revocation, current-account retrieval, account provisioning, tenant/location application authorization, and authenticated order audit identity. It also exposes anonymous but CSRF-protected tenant registration, which rate-limits attempts before BCrypt and atomically creates one tenant, its first location, and its first active administrator with a required normalized email; registration creates no session. The panel route remains a Server Component and renders one interactive staff-panel boundary. Its signed-out surface provides Sign in and Register tenant tabs, while its authenticated Orders/Accounts workspace exposes capability-gated manager/operator provisioning. The SWR current-account gate and shared native-`fetch` client add CSRF headers to unsafe requests, serialize authentication-cookie mutations with a browser Web Lock when available, recheck the current session after acquiring that lock, and perform at most one refresh and request retry after `401`. Tenant registration uses the shared CSRF client but never acquires the authentication-cookie lock or starts session recovery. Authentication credentials remain unavailable to JavaScript. Treat one browser profile as one signed-in account: same-account tabs are best-effort, immediate cross-tab UI synchronization is not required, and different accounts in different tabs are unsupported. Standalone account self-registration, additional tenant administrators, later location creation, email verification, recovery, invitations, and CAPTCHA remain excluded. OAuth2/OIDC login, PostgreSQL RLS, WebSocket delivery, an OpenAPI endpoint, and generated REST clients remain deferred. Anonymous customer REST tracking remains available through SWR-managed client state backed by handwritten native `fetch` requests. Production migrations must still create an empty, deployable schema rather than demo data.

Frontend authentication implementation notes and maintenance invariants are in [`docs/FRONTEND_AUTHENTICATION.md`](docs/FRONTEND_AUTHENTICATION.md).

The Spring application and Docker images never generate JWT signing keys. Before starting local Compose, run `./scripts/init-jwt-keys.sh` to idempotently prepare the ignored `secrets/jwt-private.pem` and `secrets/jwt-public.pem` files. Compose bind-mounts `secrets/` read-only into the API container. Production deployments require externally managed key resources.

## Documentation Synchronization
* Do not leave accepted project decisions only in the conversation. When discussion with the user changes or clarifies architecture, requirements, scope, security, data ownership, technology choices, or development conventions, update the relevant documentation in the same task.
* Update `docs/REQUIREMENTS.md` for product behavior, architecture, security, persistence concepts, integration contracts, and acceptance criteria.
* Update `AGENTS.md` for repository-wide technology choices, boundaries, workflows, and implementation conventions agents must follow.
* Keep documentation and code consistent. When a code change alters documented behavior or structure, update the affected documentation alongside the code; when a documentation decision affects existing code, identify and reconcile the mismatch rather than silently ignoring it.

## Agent Development Workflow
* Before changing files, inspect `git status --short`. Do not inspect or exercise the running Compose stack unless runtime verification is specifically requested or necessary to diagnose a reported runtime problem.
* Prefer reading code, static validation, and selective automated tests during rapid development. Browser and manual runtime smoke tests are opt-in and should be performed only when the user specifically requests them.
* If runtime verification is requested, assume an already-running stack belongs to the user and was started with `docker compose up --build --watch`; do not start a duplicate Watch process. If the stack is not running, start it only when the requested verification requires it and keep that process active for the task.
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
  * Frontend change: run that app's `check` script when practical.
  * Backend change: prefer compilation and focused tests; run the full `./mvnw test` suite from `apps/api` when the change is high risk, the user requests it, or focused coverage is insufficient.
  * Shared REST contract change: statically validate every consumer and run the relevant backend and frontend checks when practical.
  * Always run `git diff --check`.
  * Reserve production builds for dependency, build configuration, or Dockerfile changes, explicit release verification, or when the task specifically requires them.
* A changed Flyway migration can be rejected by a persistent local database because its recorded checksum no longer matches. Report the failure and ask before any database-volume reset; never erase the volume automatically.
* In the final report, distinguish checks that passed, failed, were blocked by the environment, or were not run. Do not describe stale containers or interrupted commands as successfully verified.

## Local Development
```bash
cp .env.example .env
./scripts/init-jwt-keys.sh
docker compose up --build --watch
```
* Customer app: https://app.localhost
* Panel app: https://panel.localhost
* API: https://api.localhost
