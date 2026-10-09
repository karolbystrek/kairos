# Local development and data safety

## Contents

- [Local development and data safety](#local-development-and-data-safety)
- [Contents](#contents)
- [Development schema policy](#development-schema-policy)
- [Worktree and runtime safety](#worktree-and-runtime-safety)
- [Setup](#setup)

## Development schema policy

- Until the user changes this policy, treat Kairos as a fresh development
  repository. Existing local accounts and data need no migration or backward
  compatibility. Edit `V1__create_initial_schema.sql` directly when appropriate
  rather than adding migrations solely to preserve development data. Do not
  ask again whether existing accounts/data must be preserved. This policy does
  not itself authorize stopping containers or resetting local data.
## Worktree and runtime safety

- Before changing files, inspect `git status --short` and preserve unrelated
  worktree changes.
- Do not inspect or exercise the running Compose stack unless runtime
  verification is requested or needed to diagnose a runtime problem.
- Treat running containers and data as user-owned. Never stop the full stack,
  run `docker compose down`, delete volumes, reset PostgreSQL, or prune Docker
  state without explicit authorization.
- If a changed Flyway migration conflicts with a persistent database checksum,
  report it and ask before resetting data.

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
`PostgresTestDatabase.ownerDatabase()`; this baseline does not enforce RLS yet.
