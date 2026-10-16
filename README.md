# Kairos

## Contents

- [Kairos](#kairos)
- [Contents](#contents)
- [Local development](#local-development)
- [Hosted VM preparation](#hosted-vm-preparation)
- [Repository checks](#repository-checks)
- [Stopping and resetting the local environment](#stopping-and-resetting-the-local-environment)
- [Documentation](#documentation)

Kairos is a virtual pager system for restaurants. Customers scan an
order-specific QR code to follow its status without creating an account or
installing an app. Restaurant staff manage orders through a separate
authenticated panel.

The system consists of three applications:

* `apps/customer-app` — customer-facing Next.js PWA
* `apps/panel-app` — staff Next.js panel
* `apps/api` — Spring Boot API

## Local development

Install [Docker](https://docs.docker.com/get-docker/), Node.js 22 and Java 25.
Docker runs only the infrastructure (PostgreSQL and Redis); the applications run
on your machine with hot reload over plain HTTP.

```bash
./setup.sh --dev
```

`--dev` creates `.env` from `.env.example` (an existing `.env` is kept),
generates development keys in `secrets/`, installs the frontend dependencies and
starts PostgreSQL and Redis on loopback ports. Rerunning it is safe. Run
`./setup.sh` without a preset to answer each step yourself.

Then start each application in its own terminal (or from your IDE, with
`apps/api` as the API's working directory):

```bash
(cd apps/api && ./mvnw spring-boot:run)
```

```bash
npm --prefix apps/customer-app run dev
```

```bash
npm --prefix apps/panel-app run dev
```

* Customer app: http://localhost:3000
* Staff panel: http://localhost:3001
* API: http://localhost:8080/api

The frontends use Fast Refresh. The API uses Spring Boot DevTools and restarts
in about a second after a recompile (build in your IDE, or run
`./mvnw compile` in `apps/api`). Session cookies are `Secure`, which Chrome and
Firefox accept on `http://localhost`; Safari does not, so use Chrome or Firefox.

The API reads the same configuration keys locally and in production: locally
from `.env`, in production from the environment that `compose.production.yaml`
passes to its container. NGINX, TLS and the application images exist only in
that production overlay.

Upgrading a checkout that used the earlier stack keeps the database: run
`docker compose down --remove-orphans` (without `-v`) to remove the old gateway,
application and ZITADEL containers, then `./setup.sh --dev --replace-env`, which
replaces `.env` from `.env.example`, so re-apply any personal edits.

The API handles email/password authentication itself and stores password
hashes in PostgreSQL; there is no separate identity service to run. No Google
project, browser auth SDK, SMTP or email verification is required locally. Public Create account
immediately creates a tenant administrator; managers/operators join through
manually shared invitations. Sessions survive service restarts and expire after
30 days without activity. See [authentication setup](docs/authentication-setup.md).

## Hosted VM preparation

For a repeatable Ubuntu 24.04 x86-64 host setup, follow the
[VM bootstrap guide](docs/deployment/BOOTSTRAP.md). It installs host dependencies and
prepares persistent application keys without starting Kairos.

## Repository checks

Run the checks from the repository root:

```bash
npm --prefix apps/customer-app run check
npm --prefix apps/panel-app run check
(cd apps/api && ./mvnw --batch-mode verify)
git diff --check
```

## Stopping and resetting the local environment

Stop each application with Ctrl-C. `docker compose stop` stops PostgreSQL and
Redis and keeps their data; `docker compose up -d --wait` starts them again.


To delete the Compose containers and volumes and the generated keys, and move
`.env` aside as `.env.old`:

```bash
./reset.sh
./setup.sh --dev
```

## Documentation

* [Agent instructions and development workflow](AGENTS.md)
* [Problem description](docs/PROBLEM_DESCRIPTION.md)
* [Product requirements and architecture](docs/REQUIREMENTS.md)
* [Public production operations](docs/deployment/RUNBOOK.md)
