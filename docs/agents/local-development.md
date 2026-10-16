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

Run `./setup.sh --dev` to prepare `.env` and development keys, install frontend
dependencies and start PostgreSQL and Redis (`compose.yaml`, loopback ports
`5432` and `6379`). Run the applications on the host:

- API: `./mvnw spring-boot:run` from `apps/api`, on `http://localhost:8080/api`.
  It imports `../../.env` (the same keys production passes as environment
  variables) and reads keys from `KAIROS_SECRETS_DIRECTORY` (`../../secrets`).
  The Maven plugin activates the `local` profile, which permits logged email.
  DevTools restarts it after a recompile.
- Frontends: `npm run dev` in `apps/customer-app` (`http://localhost:3000`) and
  `apps/panel-app` (`http://localhost:3001`). Each committed `.env.development`
  holds the app's `PORT` and local origins; the dev script exports it before
  starting Next.js, which binds its port before reading `.env` files.

NGINX, TLS, Cloudflare trust and the application images exist only in
`compose.production.yaml`; `tests/gateway-test.py` covers them. Web Push is
disabled under `next dev`; check it with a production build (`npm run build &&
npm start`). A phone cannot reach `localhost`, so phone testing needs a tunnel
or a LAN-reachable origin set in `.env` and `.env.development`.

For runtime verification use the default `kairos` Compose project, never a
separately named stack. Use `./reset.sh` only when the user has authorized
resetting containers or data.

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
