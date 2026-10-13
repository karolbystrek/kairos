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

Run `./setup.sh` to prepare local configuration and externally managed key
files. Start Compose separately with `docker compose up --build`. Use
`./reset.sh` only when the user has authorized resetting containers or data.
Source is not synchronized into the production-mode containers; rebuild and
recreate only affected services when runtime verification requires updated
code.

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
