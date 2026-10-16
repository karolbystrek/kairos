# Kairos

## Contents

- [Kairos](#kairos)
- [Contents](#contents)
- [Local development](#local-development)
- [Hosted VM preparation](#hosted-vm-preparation)
- [Repository checks](#repository-checks)
- [Resetting the local environment](#resetting-the-local-environment)
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
Docker runs only the infrastructure (PostgreSQL, Redis and ZITADEL); the
applications run on your machine with hot reload over plain HTTP.

```bash
./setup.sh --dev
docker compose up -d
```

`--dev` creates `.env` from `.env.example` (an existing `.env` is kept) and
generates development keys in `secrets/`. Run `./setup.sh` without a preset to
answer each step yourself.

Then start each application in its own terminal:

```bash
(cd apps/api && ./mvnw spring-boot:run)
```

```bash
npm --prefix apps/customer-app run dev
```

```bash
PORT=3001 npm --prefix apps/panel-app run dev
```

Both frontends default to port 3000, so the panel takes its port from `PORT`
(Next.js reads it before `.env` files load, so it cannot live there).

* Customer app: http://localhost:3000
* Staff panel: http://localhost:3001
* API: http://localhost:8080/api

The frontends use Fast Refresh. The API uses Spring Boot DevTools and restarts
in about a second after a recompile (build in your IDE, or run
`./mvnw compile` in `apps/api`). Session cookies are `Secure`, which Chrome and
Firefox accept on `http://localhost`; Safari does not, so use Chrome or Firefox.

To run the whole stack as production-built containers instead, without the
production NGINX gateway:

```bash
docker compose --profile apps up --build
```

Upgrading a checkout that used the earlier HTTPS gateway keeps all data:
stop the old containers with `docker compose down` (without `-v`), then
refresh `.env` with `./setup.sh --dev --replace-env`, which replaces it from
`.env.example`, so re-apply any personal edits.

Compose includes self-hosted ZITADEL for email/password authentication. Setup
creates its master key; first startup initializes a separate provider database
and provisions backend service credentials automatically. No Google project,
browser auth SDK, SMTP or email verification is required. Public Create account
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

## Resetting the local environment

To delete the Compose containers and volumes, generated keys and the ZITADEL
API token, and move `.env` aside as `.env.old`:

```bash
./reset.sh
./setup.sh --dev
```

## Documentation

* [Agent instructions and development workflow](AGENTS.md)
* [Problem description](docs/PROBLEM_DESCRIPTION.md)
* [Product requirements and architecture](docs/REQUIREMENTS.md)
* [Public production operations](docs/deployment/RUNBOOK.md)
