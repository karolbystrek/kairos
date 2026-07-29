# Kairos

Kairos is a virtual pager system for restaurants. Customers track an order in
the customer PWA, while restaurant staff manage orders in a separate panel.

## Run locally

Prepare the local environment:

```bash
./setup.sh
```

Start the applications when needed:

```bash
docker compose up --build
```

The local applications are available at:

* Customer app: https://app.localhost
* Staff panel: https://panel.localhost
* API: https://api.localhost

## Reset the local environment

Run the reset:

```bash
./reset.sh
```

The script resets the complete local environment without asking for
confirmation.

Product behavior and architecture are documented in
[`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md).
