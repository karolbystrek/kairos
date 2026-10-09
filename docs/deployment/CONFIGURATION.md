# Persistent configuration and keys

## Contents

- [Persistent configuration and keys](#persistent-configuration-and-keys)
- [Contents](#contents)
- [2. Prepare persistent configuration and keys](#2-prepare-persistent-configuration-and-keys)

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## 2. Prepare persistent configuration and keys

Use the verified VM bootstrap described in [BOOTSTRAP.md](BOOTSTRAP.md) to
prepare application keys and `/srv/kairos/production.env.example`. Bootstrap
uses the repository-owned `deployment/production.env.example`, which includes
production hostnames and safe hosted defaults but no passwords. Reruns refresh
that example, preserve existing `production.env` bytes and validate/reuse keys.

Follow the administrator SSH and copy/edit commands in that guide to prepare
`/srv/kairos/production.env` with mode `0600`. Never overwrite an existing
production file just to adopt new defaults; reconcile public settings while
preserving credentials. `.env.example` remains the local configuration inventory.
Do not source environment files as shell scripts or print resolved Compose
configuration into tickets/logs.

Setup generates the stable ZITADEL master key, webhook and push encryption keys,
and matching P-256 VAPID pair. Do not use `--replace-keys` to make startup pass.
Never discard the master key while retaining ZITADEL's database. Keep a protected copy of runtime configuration, application keys and TLS material
outside the VPS for a fresh rebuild. Database backups are deferred under the
[development operations policy](../requirements/routing-releases.md#development-operations-policy).

| Variables | Production value or responsibility |
| --- | --- |
| `COMPOSE_FILE` | `compose.yaml:compose.deployment.yaml` for manual Compose use; deploy.sh explicitly selects its files |
| `POSTGRES_USER`, `POSTGRES_DB`, `POSTGRES_PASSWORD` | Initialization/admin identity and database name; never the API datasource |
| `KAIROS_DB_OWNER_USER`, `KAIROS_DB_OWNER_PASSWORD` | Restricted migration owner; unique role/password |
| `KAIROS_DB_RUNTIME_USER`, `KAIROS_DB_RUNTIME_PASSWORD` | Restricted application datasource role; separate unique role/password |
| `ZITADEL_DB`, `ZITADEL_DB_USER`, `ZITADEL_DB_PASSWORD` | Separate provider database/user and strong password; keep names stable |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `redis`, `6379`, and a unique strong password |
| `NEXT_PUBLIC_CUSTOMER_APP_URL`, `PANEL_APP_URL`, `NEXT_PUBLIC_API_BASE_URL` | Three distinct `https://` origins without path/query/fragment; match exact gateway hosts |
| `KAIROS_CUSTOMER_APP_HOST`, `KAIROS_PANEL_APP_HOST`, `KAIROS_API_HOST` | Hostnames only; chosen customer, panel and API names |
| `KAIROS_APPLICATION_SECRETS_DIRECTORY`, `KAIROS_TLS_DIRECTORY` | `/srv/kairos/secrets`, `/srv/kairos/tls` |
| `WEBHOOK_DESTINATION_POLICY`, `PUSH_DESTINATION_POLICY` | Both `PUBLIC_HTTPS`; never carry the local webhook relaxation into production |
| `VAPID_SUBJECT` | Operator-owned `mailto:` contact or public HTTPS contact URL |
| `ZITADEL_FIRSTINSTANCE_ORG_HUMAN_PASSWORD` | Strong administrator password, not the example's local default |
| `ZITADEL_FIRSTINSTANCE_ORG_MACHINE_PAT_EXPIRATIONDATE` | Deliberate bootstrap credential expiry with renewal scheduled before it |
| `ZITADEL_API_URL`, `ZITADEL_TOKEN_LOCATION` | Keep `http://zitadel:8080`, `file:/run/zitadel/kairos.pat`; provider stays private |
| `ZITADEL_EXTERNALDOMAIN`, `ZITADEL_EXTERNALPORT`, `ZITADEL_EXTERNALSECURE`, `ZITADEL_TLS_ENABLED` | Keep private `zitadel`, `8080`, `false`, `false`; NGINX owns public HTTPS |
| `KAIROS_IMAGE_REGISTRY`, `KAIROS_RELEASE_VERSION` | deploy.sh supplies repository registry and manifest revision; operator values are not used for its digest selection |
| `NGINX_ENVSUBST_FILTER` | Keep `^KAIROS_`; hosted overlay supplies TLS and trusted-proxy settings |

Generate database, Redis and provider passwords with a password manager or
`openssl rand -hex 32`, storing output directly in protected configuration.
Changing passwords in the environment does not rotate existing PostgreSQL roles
or an already initialized provider administrator. Coordinate credential changes
with their owning service; never reinitialize databases to apply them.

Database role separation and transaction-local RLS are implemented in the
repository. Follow [PostgreSQL roles and initialization](DATABASE.md) for fresh
startup and the disposable development schema policy. The operator will recreate
the VPS database for this revision; ordinary deployment never resets volumes or
repairs an old checksum. Live runtime
isolation and application acceptance remain required before public launch.
