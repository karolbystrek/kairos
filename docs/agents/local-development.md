# Local development and data safety

## Contents

- [Local development and data safety](#local-development-and-data-safety)
- [Contents](#contents)
- [Worktree and runtime safety](#worktree-and-runtime-safety)
- [Setup](#setup)

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

Development uses HTTP on loopback; NGINX and TLS belong to production.
Web Push is disabled under `next dev`; use a production build for PWA/push
acceptance. Phone testing needs reachable origins rather than `localhost`.

For runtime verification use the default `kairos` Compose project, never a
separately named stack. Use `./reset.sh` only when the user has authorized
resetting containers or data; it refuses to run when `COMPOSE_FILE` selects
production.

Backend verification requires a running Docker engine. Run `./mvnw --batch-mode verify`
from `apps/api` (or `apps/api/mvnw -f apps/api/pom.xml --batch-mode verify`
from the repository root). The JUnit launcher starts one disposable PostgreSQL 18
container per session and removes it afterward. Missing Docker fails verification;
there is no H2 fallback or silently skipped database suite. Tests never reuse
Compose containers or user-owned volumes. Fixture setup can use
`PostgresTestDatabase.ownerDatabase()` for committed setup, independent assertions
and cleanup. Application calls use the actual restricted runtime role and their
own scoped transactions; do not wrap multiple actors or owner fixtures in a
test-wide transaction. Missing context fails closed.
