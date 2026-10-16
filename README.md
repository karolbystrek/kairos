# Kairos

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

The first start builds the images and takes a few minutes. Register your first
staff account at the panel URL. First login, daily commands, logs, single-service
rebuilds and Web Push limits are in
[local development](docs/agents/local-development.md). Production from a source
checkout is described in [production configuration](docs/deployment/CONFIGURATION.md);
hosted releases use the [production runbook](docs/deployment/RUNBOOK.md).

To delete the Compose containers and volumes and the generated keys, and move
`.env` aside as `.env.old` (refuses to run in production mode, asks before
deleting unless given the skip flag):

```bash
./reset.sh
./setup.sh --dev
```

## Hosted VM preparation

For a repeatable Ubuntu 24.04 x86-64 host setup, follow the
[VM bootstrap guide](docs/deployment/BOOTSTRAP.md). It installs host dependencies and
prepares persistent application keys without starting Kairos.

## Repository checks

Frontend and backend check commands are listed in [validation](docs/agents/validation.md).

## Documentation

* [Agent instructions and development workflow](AGENTS.md)
* [Problem description](docs/PROBLEM_DESCRIPTION.md)
* [Product requirements and architecture](docs/REQUIREMENTS.md)
* [Public production operations](docs/deployment/RUNBOOK.md)
