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

Run `./setup.sh --dev` to prepare `.env` and development keys and start the API,
both frontends, PostgreSQL and Redis in Docker. No host application commands
are required. Setup selects `compose.yaml` plus `compose.local.yaml` in `.env`;
`docker compose up --build -d` subsequently starts or rebuilds that mode.

- Customer: `http://localhost:3000`; panel: `http://localhost:3001`;
  API: `http://localhost:8080/api`.
- Source files are mounted from the checkout. Next.js provides Fast Refresh;
  the API development image uses `watchexec` to rebuild and restart after Java
  or resource edits. A compile error leaves the API unavailable until the next
  successful edit; the watcher stays running.
- Dependencies and frontend build output use container-owned volumes; API build
  output stays inside its container. Host artifacts do not interfere. Rebuild
  after changing dependency descriptors or Dockerfiles.
- `docker compose logs -f` follows logs; `docker compose stop` stops all services
  without deleting data.
- `./setup.sh --prod` selects `compose.yaml` plus `compose.production.yaml`,
  validates the supplied configuration and starts the production stack. Existing
  settings and keys are preserved; supply missing production values and TLS
  material before retrying. A copied setup script without Compose retains the
  VM bootstrap's keys-only behavior.

## First start and daily use

- The first start builds all images and downloads dependencies; expect a few
  minutes. Wait until `docker compose ps` shows the services as healthy.
- First login: open the panel at `http://localhost:3001` and register. Local
  mode sets `EMAIL_VERIFICATION_ENABLED=false` (`.env.example`), so
  registration completes without a code and opens first-location onboarding.
  Set it to `true` in `.env` and restart the API to exercise verification.
- No email is sent locally: `compose.local.yaml` sets `EMAIL_DELIVERY_MODE=LOG`.
  The API logs each email attempt with its content redacted, so codes and
  invitation links are not available locally; keep verification disabled.
- Single services use the Compose names `api`, `panel-app`, `customer-app`,
  `postgres` and `redis`:
  - `docker compose up --build -d api` rebuilds and restarts one service.
  - `docker compose logs -f panel-app` follows one service.
  - `docker compose restart customer-app` restarts without rebuilding.
  - `docker compose ps` shows state and health.
- `./reset.sh` deletes containers, volumes and keys, without asking
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
