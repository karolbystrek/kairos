# Kairos

Kairos is a virtual pager system for restaurants. Customers scan an
order-specific QR code to follow its status without creating an account or
installing an app. Restaurant staff manage orders through a separate
authenticated panel.

The system consists of three independently deployable applications:

* `apps/customer-app` — customer-facing Next.js PWA
* `apps/panel-app` — staff Next.js panel
* `apps/api` — Spring Boot API

## Local development

Install [Docker](https://docs.docker.com/get-docker/) and
[mkcert](https://github.com/FiloSottile/mkcert), then trust the local certificate
authority once:

```bash
mkcert -install
```

Prepare the local configuration and development keys:

```bash
./setup.sh
```

Build and start Kairos:

```bash
docker compose up --build
```

The applications are available at:

* Customer app: https://customer.kairos.localhost
* Staff panel: https://panel.kairos.localhost
* API: https://api.kairos.localhost

## Repository checks

Run the checks from the repository root:

```bash
npm --prefix apps/customer-app run check
npm --prefix apps/panel-app run check
(cd apps/api && ./mvnw --batch-mode verify)
git diff --check
```

## Resetting the local environment

To reset the local containers, persisted data, configuration, and generated key
material:

```bash
./reset.sh
```

This deletes the Compose volumes and generated keys.

## Documentation

* [Problem description](docs/PROBLEM_DESCRIPTION.md)
* [Product requirements and architecture](docs/REQUIREMENTS.md)
* [Private staging deployment](deployment/RUNBOOK.txt)
