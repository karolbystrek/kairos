# Local development and data safety

## Worktree and runtime safety

- Before changing files, inspect `git status --short` and preserve unrelated
  worktree changes.
- Do not inspect or exercise the running Compose stack unless runtime
  verification is requested or needed to diagnose a runtime problem.
- Treat running containers and data as user-owned. Never stop the full stack,
  run `docker compose down`, delete volumes, reset PostgreSQL, or prune Docker
  state without explicit authorization.
- If a changed Flyway migration conflicts with a persistent database checksum,
  report it and ask before resetting data; never run Flyway repair automatically.

## Setup

Run `make dev` to start the API, the web application, PostgreSQL and Redis in
Docker. No host application commands are required. It copies `.env.example` to
`.env` and generates development keys in `secrets/` only when they are missing,
so rerunning it rebuilds the stack without touching existing configuration or
keys. `.env.example` selects `compose.yaml` plus `compose.local.yaml`. New
settings added to `.env.example` later are not merged into an existing `.env`;
copy them by hand or run `make reset`. `make` alone lists the commands.

- Web application: `http://localhost:3000` (customer PWA under `/orders`, staff
  panel under `/panel`); API: `http://localhost:8080/api`. `next dev` proxies
  `/api` to the API, as NGINX does in production.
- Source files are mounted from the checkout. Next.js provides Fast Refresh;
  the API development image uses `watchexec` to rebuild and restart after Java
  or resource edits. A compile error leaves the API unavailable until the next
  successful edit; the watcher stays running.
- Dependencies and frontend build output use container-owned volumes; API build
  output stays inside its container. Host artifacts do not interfere. Rebuild
  after changing dependency descriptors or Dockerfiles.
- `make logs` follows logs; `make stop` stops all services without deleting data.

## First start and daily use

- The first start builds all images and downloads dependencies; expect a few
  minutes. Wait until `docker compose ps` shows the services as healthy.
- First login: open the panel at `http://localhost:3000/panel` and register. Local
  mode sets `EMAIL_VERIFICATION_ENABLED=false` (`.env.example`), so
  registration completes without a code and opens first-location onboarding.
  Set it to `true` in `.env` and restart the API to exercise verification.
- No email is sent locally: `compose.local.yaml` sets `EMAIL_DELIVERY_MODE=LOG`.
  The API logs each email attempt with its content redacted, so codes and
  invitation links are not available locally; keep verification disabled.
- Single services use the Compose names `api`, `web`, `postgres` and `redis`:
  - `docker compose up --build -d api` rebuilds and restarts one service.
  - `docker compose logs -f web` follows one service.
  - `docker compose restart web` restarts without rebuilding.
  - A stack started before the single-origin merge still has `customer-app` and
    `panel-app` containers; `docker compose up -d --remove-orphans` removes
    them once the user approves.
  - `docker compose ps` shows state and health.
- `make reset` deletes containers, volumes and keys, without asking
  and refuses to run when `COMPOSE_FILE`, in `.env` or exported in the environment, selects
  production.

## Runtime limits and test safety

Development uses HTTP on loopback; NGINX and TLS belong to production.
Web Push is disabled under `next dev`; use a production build for PWA/push
acceptance. Phone testing needs reachable origins rather than `localhost`.
Session cookies work over `http://localhost` in Chrome and Firefox; Safari
requires HTTPS.

For runtime verification use the `kairos` Compose project (`name: kairos` in
`compose.yaml`), never a separately named stack.

Backend verification requires a running Docker engine; the command is in
[validation](validation.md). The JUnit launcher starts one disposable PostgreSQL 18
container per session and removes it afterward. Missing Docker fails verification;
there is no H2 fallback or silently skipped database suite. Tests never reuse
Compose containers or user-owned volumes. Fixture setup can use
`PostgresTestDatabase.ownerDatabase()` for committed setup, independent assertions
and cleanup. Application calls use the actual restricted runtime role and their
own scoped transactions; do not wrap multiple actors or owner fixtures in a
test-wide transaction. Missing context fails closed.
