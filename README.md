# Kairos

Kairos is a virtual pager system for restaurants. Customers track an order in
the customer PWA, while restaurant staff manage orders in a separate panel.

## Run locally

Prerequisites:

* Docker with Docker Compose 2.32.2 or newer
* OpenSSL

Prepare the local environment:

```bash
./setup.sh
```

The setup script copies `.env.example` to `.env` and creates all local signing
and encryption keys. If a file already exists, the script asks before replacing
it. After preparation, it starts the application with Docker Compose Watch.
Keep that terminal open while developing.

The local applications are available at:

* Customer app: https://app.localhost
* Staff panel: https://panel.localhost
* API: https://api.localhost

## Reset the local environment

Run the interactive reset:

```bash
./reset.sh
```

The reset script independently asks whether to:

* remove the Kairos containers and all Compose volumes, including PostgreSQL,
  Redis, and Caddy data;
* replace `.env` from `.env.example`, saving the previous file as `.env.old`.

Local signing and encryption keys are regenerated only when Compose volume data
is removed. Otherwise, existing keys are validated and reused so encrypted
database records remain readable.

To confirm both reset actions without prompts, run:

```bash
./reset.sh -y
```

After resetting, run `./setup.sh` to start Kairos again.

Product behavior and architecture are documented in
[`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md).
