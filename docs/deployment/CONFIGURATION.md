# Persistent configuration and keys

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## Prepare persistent configuration and keys

Use the verified VM bootstrap described in [BOOTSTRAP.md](BOOTSTRAP.md) to
prepare application keys. Hosted release configuration is owned by the GitHub
`production` Environment, never edited on the host. Every deployment writes
`/srv/kairos/production.env` (mode `0600`, replaced atomically) from the
committed `deployment/production.env.example` plus that Environment's values:

- The template lists every production setting. A setting with a template
  default needs no GitHub value.
- Every blank template setting except `KAIROS_IMAGE_REGISTRY` and
  `KAIROS_RELEASE_VERSION` is required. Sensitive values are Environment
  secrets and public ones are variables.
- The deploy step in `.github/workflows/deploy.yml` names every required key
  explicitly (`secrets.NAME` or `vars.NAME`). It never passes all secrets at
  once: GitHub flags `toJSON(secrets)` as possible exfiltration and holds the run.
- Missing values, or values containing `'` or a newline, fail deployment before
  any SSH connection, naming keys only. Values are written single-quoted, so `$`
  is literal.
- Values reach the host only over the deploy job's SSH connection, never
  images, release bundles or artifacts.

For a source checkout outside the hosted release workflow, `./setup.sh --prod`
creates or preserves `.env`, selects the production Compose overlay, and starts
it once required values and TLS files are supplied. Production source builds use
the same Dockerfiles; the hosted deployer continues to use pinned images with
`--no-build`. Missing configuration fails setup without resetting data or keys.

**Adding a required production setting:** whenever a change adds a blank
(required) key to `deployment/production.env.example`, also add its value to
the GitHub `production` Environment (secret or variable) and map it in the
deploy step of `deploy.yml`, in the same change. Set the value before merging,
otherwise the next deployment fails on the missing key. Overriding a defaulted
setting from GitHub likewise needs both the Environment value and the mapping.

Set the required values with `gh secret set NAME --env production --repo
karolbystrek/kairos` (it prompts for the value) or `gh variable set`.
`.env.example` remains the local configuration inventory. Do not source
environment files as shell scripts or print resolved Compose configuration into
tickets/logs.

Setup generates the webhook, push and email encryption keys,
and matching P-256 VAPID pair. Do not use `--replace-keys` to make startup pass.
Keep a protected copy of application keys and TLS material
outside the VPS for a fresh rebuild; runtime configuration already lives in GitHub. Database backups are deferred under the
[development operations policy](../requirements/routing-releases.md#development-operations-policy).

| Variables | Production value or responsibility |
| --- | --- |
| `COMPOSE_FILE` | `compose.yaml:compose.production.yaml` for manual Compose use; deploy.py explicitly selects its files |
| `POSTGRES_USER`, `POSTGRES_DB`, `POSTGRES_PASSWORD` | Initialization/admin identity and database name; never the API datasource |
| `KAIROS_DB_OWNER_USER`, `KAIROS_DB_OWNER_PASSWORD` | Restricted migration owner; unique role/password |
| `KAIROS_DB_RUNTIME_USER`, `KAIROS_DB_RUNTIME_PASSWORD` | Restricted application datasource role; separate unique role/password |
| `POSTGRES_HOST` | `postgres`, the Compose service name the API connects to |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `redis`, `6379`, and a unique strong password |
| `CUSTOMER_APP_URL`, `PANEL_APP_URL` | `https://` site URLs without path/query/fragment; match the exact gateway hosts; read at runtime by the API and frontends |
| `KAIROS_CUSTOMER_APP_HOST`, `KAIROS_PANEL_APP_HOST`, `KAIROS_API_HOST` | Three distinct hostnames only; chosen customer, panel and API names |
| `KAIROS_SECRETS_DIRECTORY` | `/run/secrets`, where the API reads the mounted secret files in its container |
| `KAIROS_APPLICATION_SECRETS_DIRECTORY`, `KAIROS_TLS_DIRECTORY` | `/srv/kairos/secrets`, `/srv/kairos/tls`; Compose file secrets mounted read-only under `/run/secrets/` (local default `./secrets`) |
| `WEBHOOK_DESTINATION_POLICY`, `PUSH_DESTINATION_POLICY` | Both `PUBLIC_HTTPS`; never carry the local webhook relaxation into production |
| `EMAIL_DELIVERY_MODE`, `RESEND_API_KEY` | Hosted mode `RESEND` and a Resend sending key; the API refuses production startup without it. Local/test mode `LOG` sends no mail and logs no message secrets. |
| `VAPID_SUBJECT` | Operator-owned `mailto:` contact or public HTTPS contact URL |
| `KAIROS_IMAGE_REGISTRY`, `KAIROS_RELEASE_VERSION` | deploy.py supplies repository registry and manifest revision; operator values are not used for its digest selection |
| `KAIROS_API_USER` | `uid:gid` the API container runs as so it can read the owner-only secret files; deploy.py and `setup.sh --prod` set it to the current user |
| `REVIEW_INVITATION_DELAY` | Delay between order completion and the optional Google review invitation; default `30m` |
| `EMAIL_VERIFICATION_ENABLED` | Not in the template; the API defaults to `true`. Keep verification enabled in production (`false` is for local development) |
| `NEXT_TELEMETRY_DISABLED` | `1`; disables Next.js telemetry in the frontend images |
| `NGINX_ENVSUBST_FILTER` | Keep `^KAIROS_`; hosted overlay supplies TLS and trusted-proxy settings |

Generate database and Redis passwords with a password manager or
`openssl rand -hex 32`, storing output directly in GitHub secrets.
Database passwords apply only when the database is first initialized. Changing
them in GitHub later does not rotate existing PostgreSQL roles, and the next deployment
then fails authentication. Coordinate credential changes with their owning
service as separate maintenance; never reinitialize databases to apply them.

Database role separation and transaction-local RLS are implemented in the
repository. Follow [PostgreSQL roles and initialization](DATABASE.md) for fresh
startup. Live runtime isolation and application acceptance remain required before
public launch.

### Transactional email (Resend)

Kairos sends Polish mail as `Kairos <no-reply@kairos.karolbystrek.pl>` through
the Resend HTTP API. Every step below changes
a live resource and requires explicit approval for its scope.

1. In Resend, add the domain `kairos.karolbystrek.pl`. Create the DKIM TXT
   record and the `send` MX and SPF TXT records it lists in Cloudflare as
   **DNS only** (unproxied), and keep a `_dmarc` TXT record (`p=none` while
   monitoring delivery). Wait until Resend reports the domain as verified.
2. In the Resend domain settings, disable open and click tracking. Kairos sends
   no tracking options, so this setting is what keeps tracking off.
3. Create an API key with **Sending access** restricted to that domain. Set it
   as the `RESEND_API_KEY` production Environment secret; the template keeps
   `EMAIL_DELIVERY_MODE=RESEND`. Never commit, print or paste the key. Deployment
   fails before changing containers when it is missing.
4. Allow outbound HTTPS from the host to `api.resend.com:443`. Lightsail permits
   outbound traffic by default; check this only where egress filtering exists.

Bootstrap generates the dedicated 32-byte
`/srv/kairos/secrets/email-encryption.bin`, mounted read-only at
`/run/secrets/email-encryption.bin`. Keep a protected off-host copy with the
other keys: queued messages cannot be decrypted without it. Never print codes,
invitation tokens or pending message contents.

After the first deployment with email, register with an inbox you control and
confirm that the Polish verification code arrives from the sender above and
completes registration. Then invite a second inbox and redeem the invitation
with that address. The panel's invitation history must show the delivery as
sent, and the Resend dashboard must list both messages. Check the spam folder
before tightening DMARC.
