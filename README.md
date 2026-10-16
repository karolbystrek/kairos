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

Install [Docker](https://docs.docker.com/get-docker/) with Compose and OpenSSL, then run:

```bash
./setup.sh --dev
```

This prepares configuration and keys, builds the images, and starts the API,
both frontends, PostgreSQL and Redis in Docker. Source edits reload the
applications automatically; no host Node.js or Java installation is needed.

* Customer app: http://localhost:3000
* Staff panel: http://localhost:3001
* API: http://localhost:8080/api

Start or rebuild the selected stack later with:

```bash
docker compose up --build -d
```

For production from a source checkout, use `./setup.sh --prod` after supplying
production configuration and TLS files. It selects the production stack; the
same Compose command starts it afterward. Existing configuration, database data
and keys are preserved. See [local development](docs/agents/local-development.md)
for details and [production configuration](docs/deployment/CONFIGURATION.md)
for required production settings. Hosted CI releases continue to use the
[production runbook](docs/deployment/RUNBOOK.md).

Locally, email stays in log mode and registration skips email verification.
The customer service worker and Web Push require a production build; `next dev`
provides foreground tracking and Fast Refresh. Session cookies work over
`http://localhost` in Chrome and Firefox; Safari requires HTTPS.

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

`docker compose stop` stops the full stack and keeps its data.
`docker compose up --build -d` starts it again; `docker compose logs -f` follows
application logs.


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
