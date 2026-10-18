# Kairos

A virtual pager for restaurants. Customers scan a QR code to follow their
order without an account or app; staff manage orders in a separate panel, and
point-of-sale systems integrate through a REST API and webhooks.

* `apps/customer-app`: customer Next.js PWA
* `apps/panel-app`: staff Next.js panel
* `apps/api`: Spring Boot API

## Architecture

![Kairos services overview](docs/diagrams/svg/services.svg)

### Order status notifications

![Order status notification flow](docs/diagrams/svg/status-change-flow.svg)

## Running locally

Requires [Docker](https://docs.docker.com/get-docker/) with Compose 2.30+ and OpenSSL.

```bash
./setup.sh --dev
```

Customer app: http://localhost:3000, panel: http://localhost:3001,
API: http://localhost:8080/api. More in [local development](docs/agents/local-development.md).

## Documentation

* [Problem description](docs/PROBLEM_DESCRIPTION.md)
* [Requirements and architecture](docs/REQUIREMENTS.md)
