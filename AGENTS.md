# Kairos

## Project

Kairos is a virtual pager system for restaurants. Customers anonymously track
orders from QR codes, staff manage queues through an authenticated panel, and
external systems integrate through versioned REST APIs and webhooks.

- Thesis framing: `docs/PROBLEM_DESCRIPTION.md`
- Canonical product behavior, architecture, security requirements, contracts,
  delivery status, and roadmap: `docs/REQUIREMENTS.md`

> **Development prerequisite:** Read `docs/REQUIREMENTS.md` in full before
> planning, implementing, reviewing, refactoring, or otherwise changing code,
> database migrations, APIs, security, infrastructure, or tests. Treat it as the
> source of truth throughout the task.

## Repository and Stack

```text
apps/
  customer-app/   Next.js customer PWA
  panel-app/      Next.js staff panel
  api/            Spring Boot API
docs/             Canonical product and architecture documentation
compose.yaml      Local PostgreSQL, Redis, applications, and Caddy
Caddyfile         Local reverse proxy and TLS
```

- Both frontends: Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI 3,
  Zod, native `fetch`, and SWR.
- Customer PWA: Serwist service worker, IndexedDB offline snapshots, and Web
  Push.
- API: Java 25, Spring Boot 4, Spring Security, and Spring MVC.
- Data and real time: PostgreSQL is authoritative; Redis Pub/Sub fans out
  cross-instance customer events.
- Infrastructure: Docker Compose and Caddy.
- Each application is independently deployable and owns its dependency
  manifest and Dockerfile.

## Architecture Boundaries

- Spring Boot owns business rules, authentication, authorization, tenant
  isolation, persistence, browser and external APIs, SSE, Web Push, webhooks,
  and outbox processing.
- Keep REST as the boundary between frontends, External Integrations, and the
  API. Browser families use `/api/{resource-family}/v1`; external families use
  `/api/external/{resource-family}/v1`.
- Browser-facing API and SSE traffic is exposed by Caddy on the relevant
  frontend origin. External Integrations use the dedicated API origin.
- Frontends use small handwritten request modules and response types with
  native `fetch`. Use SWR for REST-backed client state and Zod for
  frontend-owned input and event validation.
- Do not add Next.js Server Actions or proxy route handlers as an API layer in
  front of Spring.
- Customer SSE and Web Push are invalidation or notification mechanisms; REST
  remains authoritative. Tracked-order REST stays network-only in the service
  worker, and explicit IndexedDB snapshots must be labelled stale when used
  offline.
- Enforce security and tenant access in the API. Frontend redirects and hidden
  controls are user-experience features, not authorization controls.
- Keep access and refresh credentials unavailable to JavaScript.
- Keep production migrations free of environment-specific seed data.

## Implementation Conventions

### Frontend

- Always use HeroUI (`@heroui/react`) for UI components.
- Reserve React effects for synchronization with external systems such as
  EventSource or browser APIs; do not use effects for routine REST request
  orchestration.
- Keep the official `eslint-plugin-react-hooks` recommended rules enabled.
- Route panel REST calls through the shared authenticated native-`fetch`
  client, scope staff SWR keys by account ID, and clear staff-owned state when
  the authenticated account changes.

### Backend

- Organize code first by business feature and then by `api`, `application`,
  `domain`, and `infrastructure`. Add cohesive subpackages only where they
  represent a real conceptual boundary.
- Configure the application-wide `/api` base path with
  `server.servlet.context-path`; controller mappings declare resource-relative
  paths and do not repeat `/api`.
- Map projections through a static `from(...)` factory on API response records.
- Use Lombok `@RequiredArgsConstructor` for routine constructor injection,
  `@NonNull` for internal runtime null contracts, and `@Slf4j` for logging. Use
  Jakarta Bean Validation for API input.
- Prefer Spring Data derived query methods when the property path expresses the
  query; reserve manual `@Query` declarations for non-derivable or bulk
  operations.
- Use `var` when an initializer makes the local type evident. Indent Java with
  four spaces and never use tabs.

Use Conventional Commits for every commit.

## Documentation

- Record accepted product, architecture, security, data-ownership, scope, and
  contract decisions in `docs/REQUIREMENTS.md`.
- Keep `AGENTS.md` limited to durable repository-wide technology, boundaries,
  conventions, and workflows.
- Keep `README.md`, `AGENTS.md`, `docs/PROBLEM_DESCRIPTION.md`, and
  `docs/REQUIREMENTS.md` as the repository's only Markdown documentation files.
- Update documentation and implementation together when either changes the
  other.

## Agent Workflow

- Before changing files, inspect `git status --short` and preserve unrelated
  worktree changes.
- Do not inspect or exercise the running Compose stack unless runtime
  verification is requested or needed to diagnose a runtime problem.
- Automated tests and browser or manual runtime checks are opt-in; run them
  only when the user explicitly requests them. Default to static inspection and
  non-test checks.
- Use repository-owned commands:
  - Customer frontend: `npm --prefix apps/customer-app run check`
  - Panel frontend: `npm --prefix apps/panel-app run check`
  - Backend: `apps/api/mvnw` from the repository root, or `./mvnw` from
    `apps/api`
  - Frontend dependencies: run `npm install` from the affected application and
    let it update `package-lock.json`
- Do not invoke installed frontend tools through `npx`, raw binaries, or
  `node_modules/.bin`. Use `npm run` or `npm --prefix`.
- Validate the affected scope when practical and always run
  `git diff --check`. Reserve production builds for dependency, build,
  Dockerfile, release-verification, or explicitly requested work.
- Treat running containers and data as user-owned. Never stop the full stack,
  run `docker compose down`, delete volumes, reset PostgreSQL, or prune Docker
  state without explicit authorization.
- If a changed Flyway migration conflicts with a persistent database checksum,
  report it and ask before resetting data.
- Report checks as passed, failed, blocked, or not run.

## Local Setup

Run `./setup.sh` to prepare local configuration and externally managed key
files, then build and start Compose. Use `./reset.sh` only when the user has
authorized resetting containers or data. Source is not synchronized into the
production-mode containers; rebuild and recreate only affected services when
runtime verification requires updated code.
