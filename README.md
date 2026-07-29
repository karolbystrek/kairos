# Kairos

Kairos is a virtual pager system for restaurants. Customers track an order in
the customer PWA, while restaurant staff manage orders in a separate panel.

## Run locally

Prepare the local environment:

```bash
./setup.sh
```

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

* remove the Kairos containers and all Compose volume data;
* replace `.env` from `.env.example`, saving the previous file as `.env.old`.

The reset script leaves local signing and encryption keys unchanged. The setup
script is solely responsible for preparing those resources.

To confirm both reset actions without prompts, run:

```bash
./reset.sh -y
```

After resetting, run `./setup.sh` to start Kairos again.

Product behavior and architecture are documented in
[`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md).
