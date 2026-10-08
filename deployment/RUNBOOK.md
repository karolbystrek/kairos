# Public production operations

Kairos runs one Docker Compose stack on an x86-64 Linux VM behind Cloudflare.
Use this runbook for operator provisioning and approved releases. Repository
preparation does not provision a VM, configure DNS or GitHub protection, or
establish public-launch readiness. Those actions require operator inputs and
explicit authorization. Use synthetic data until the launch gates in
[issue #9](https://github.com/karolbystrek/kairos/issues/9) are verified.

## 1. Choose and prepare the host

Record the provider, supported Linux release, region, capacity, three hostnames
under one parent domain, administrator access path, and operational owner.
AWS Lightsail is a possible provider, not a requirement. Use x86-64 to match
`linux/amd64` images. Allow memory/CPU/disk headroom for PostgreSQL, ZITADEL,
Java and both frontends; the overlay's limits are bounds, not a VM sizing target.

Install Docker Engine and its Compose plugin using the
[official distribution instructions](https://docs.docker.com/engine/install/).
The host also needs Git, GitHub CLI (`gh`), Bash, Python 3.9+, OpenSSL,
curl supporting `--max-filesize`,
OpenSSH server and GNU `tar`, `mv`, `cmp` and ordinary coreutils. Verify Compose
supports `up --wait --wait-timeout` and `config --no-path-resolution`:

```bash
uname -m
python3 --version
docker version
docker compose version
docker compose up --help
docker compose config --help
curl --version
```

Use a dedicated deployment account with SSH key authentication, password login
and root SSH login disabled, no agent/port/X11 forwarding, and no public Docker
API. The existing upload command needs SSH shell access, SCP and Docker access;
a forced command must support that protocol before it can be applied. Limit
SSH ingress to approved administration and Actions egress sources, using a
maintained allowlist or an agreed restricted path compatible with GitHub-hosted
runners. Do not install a self-hosted Actions runner on this VM. Keep a provider
console recovery path before restricting SSH. Docker group/socket access grants
host-level authority; the deployment identity must be protected accordingly.

Verify the SSH host key fingerprint from the provider console or another
independently trusted channel. Prepare an OpenSSH `known_hosts` file matching
`DEPLOY_HOST` exactly; an unverified `ssh-keyscan` result is not an identity
anchor. The uploader uses port 22 and accepts a DNS name or IPv4 address for
`DEPLOY_HOST`, not a URL, IPv6 literal or `host:port`.

Have the administrator create `/srv/kairos`, owned by the deployment account.
Run the remaining host commands as that account with `umask 077`:

```bash
umask 077
mkdir -p /srv/kairos/releases /srv/kairos/tls
chmod 0700 /srv/kairos /srv/kairos/releases /srv/kairos/tls
```

| Path | Purpose and access |
| --- | --- |
| `/srv/kairos/production.env` | Persistent runtime configuration, mode `0600` |
| `/srv/kairos/secrets/` | Five application key files; directory `0700`, files `0400` |
| `/srv/kairos/tls/tls.crt`, `tls.key` | Origin certificate `0644`, private key `0600`; directory `0700` |
| `/srv/kairos/releases/<revision>/` | Disposable exact-revision bundle, private to deployment account |
| `/srv/kairos/deploy.lock` | Host-wide deployment lock |
| `/srv/kairos/deployed-release.json` | Last fully verified revision and three digests |
| Docker named volumes | `kairos_pgdata`, `kairos_zitadel-bootstrap-credentials`, `kairos_zitadel-api-credentials` |

Compose always uses project name `kairos`. PostgreSQL stores both databases in
`pgdata`; the two provider volumes store different PATs. Keep Docker's data root
on persistent storage and preserve these volumes across release directories.
Redis is nondurable. Secrets, certificates and database state never belong in a
release archive, image, issue, PR, or Actions log.

Private GHCR packages require a host credential with `read:packages` and read
access to all three packages. Authenticate Docker as the deployment account
using `docker login ghcr.io --username "$KAIROS_REGISTRY_USER" --password-stdin`,
feeding the token from a protected secret source. Prefer a credential helper;
otherwise protect that account's Docker config with `0700`/`0600`. Do not put
the token in command arguments or enable shell tracing. Public packages can be
pulled anonymously. See [GHCR authentication](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).

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
Never discard the master key while retaining ZITADEL's database. Back up the
complete key set and databases consistently before maintenance.

| Variables | Production value or responsibility |
| --- | --- |
| `COMPOSE_FILE` | `compose.yaml:compose.deployment.yaml` for manual Compose use; deploy.sh explicitly selects its files |
| `POSTGRES_USER`, `POSTGRES_DB`, `POSTGRES_PASSWORD` | Selected database identity/name and unique strong password; see privilege blocker below |
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

**Database launch blocker:** the current Compose setup shares `POSTGRES_USER`
between PostgreSQL initialization, ZITADEL administration and the API/Flyway.
That initial role is privileged; this is not a verified non-bypass runtime role.
Do not claim RLS protection or improvise GRANT/role changes here. The separately
approved RLS increment must define migration, API runtime and provider metadata
privileges plus verified tenant/location transaction context before public use.
See [tenant isolation and launch requirements](../docs/REQUIREMENTS.md#44-tenant-isolation).

## 3. Configure Cloudflare and origin ingress

Create proxied DNS records for the three chosen application hostnames pointing
to the VM. Install a Cloudflare Origin CA certificate covering all three names
at `/srv/kairos/tls/tls.crt` and its matching private key at `tls.key`, with the
permissions above. Configure **Full (strict)** encryption. Never use mkcert,
Certbot or a tunnel for this hosted path. Origin CA is trusted by Cloudflare;
direct browser access to the origin is not the certificate acceptance test.
See [Origin CA operations](https://developers.cloudflare.com/ssl/origin-configuration/origin-ca/).

Use provider/network firewall rules that apply before Docker's published-port
handling: allow inbound TCP 443 only from the current authoritative
[Cloudflare IPv4](https://www.cloudflare.com/ips-v4/) and
[IPv6](https://www.cloudflare.com/ips-v6/) ranges. Apply separately restricted
SSH rules. Deny other inbound traffic, including application/data ports and a
Docker API. Handle both address families; if IPv6 is unused, explicitly block
its ingress and do not publish an origin AAAA record.

Docker-published traffic can bypass UFW's ordinary input rules. A UFW allowlist
alone does not establish origin isolation. Use the provider firewall independently
and, if adding host filtering, account for the selected Docker firewall backend
and its forwarding rules. Do not disable Docker firewall management blindly.
Verify blocked origin access from an external non-Cloudflare network over both
families, including SNI/Host requests to the VM IP; certificate distrust alone is
not proof that ingress is blocked. See [Docker firewall behavior](https://docs.docker.com/engine/network/packet-filtering-firewalls/).

Maintain firewall allowlists and `nginx/cloudflare-real-ip.conf` together using
[the range maintenance procedure](../docs/REQUIREMENTS.md#72-hosted-ingress-and-cloudflare-range-maintenance).
NGINX trusts `CF-Connecting-IP` only from those ranges and replaces upstream
forwarding headers. Never add arbitrary VM/Docker subnets to restore forwarding.

Set Cloudflare Cache Rules to **Bypass cache** for the entire API hostname and
for `/sw.js` on the customer hostname. Do not apply cache-everything to application
pages. Exclude anonymous customer and External Integration API traffic from
interactive challenges/Access login, while retaining the origin gateway limits.
The shared limits are authentication/registration/redemption/password POSTs at
5 requests/minute per client, burst 5, and external API at 10 requests/second,
burst 20, returning `429`. CSRF/read/SSE paths remain outside auth throttling.
See [Cache Rules settings](https://developers.cloudflare.com/cache/how-to/cache-rules/settings/).
Verify edge REST cache bypass, service-worker update behavior, cross-origin
cookies/CORS/CSRF and long-lived SSE heartbeats/reconnects on the selected host.

## 4. Protect GitHub releases before enabling deployment

An authorized repository owner configures the `production` Environment before
adding deployment secrets or approving a run. A workflow reference alone may
create an unprotected Environment. Use `gh` for all GitHub operations.

Configure required reviewer `karolbystrek`, allow that owner to approve their own
initiated runs (`prevent_self_review=false`), and allow administrator bypass
(`can_admins_bypass=true`) as selected by the owner. Select custom deployment branch policies with **only branch
`main`**, no tag policy; do not use “all protected branches” as a substitute.
The owner can apply these through `gh api` using the Environment and deployment
branch-policy endpoints and verify the saved result with:

```bash
gh api repos/karolbystrek/kairos/environments/production
gh api repos/karolbystrek/kairos/environments/production/deployment-branch-policies
```

Check plan/repository visibility supports required reviewers; if unavailable,
record it as a release blocker. Administrator bypass remains allowed under the
owner-approved policy. See [GitHub Environment protection](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments).

Set non-secret **repository** variables `NEXT_PUBLIC_API_BASE_URL` and
`NEXT_PUBLIC_CUSTOMER_APP_URL` to the production values using `gh variable set`.
Validation/publication jobs run before the Environment gate and cannot read
Environment variables. Frontend URLs are compiled into images; changing these
variables requires a new source revision/build, because existing revision tags
are immutable. Keep runtime origins consistent with those build inputs.

Install these four secrets with `gh secret set --env production` from protected
files or secure prompts, never literal secret arguments or repository-level copies:

| Environment secret | Value |
| --- | --- |
| `DEPLOY_HOST` | Verified reachable deployment host |
| `DEPLOY_USER` | Dedicated restricted Linux deployment username |
| `DEPLOY_SSH_KEY` | Matching private SSH key authorized for that account |
| `DEPLOY_KNOWN_HOSTS` | Independently verified OpenSSH host-key entries |

Do not reuse the GitHub publication token as host GHCR credentials. Before the
first authorized release, inspect the main CI run: validation and all three
image publications must finish before `Deploy production` waits for approval.
While approval is withheld, no deployment steps/SSH connection may run and
Environment secrets must remain gated. Verify owner self-approval, main-only
policy, and rejected/withheld approval behavior; record results without secrets.
Merging a PR or publishing images does not authorize approving deployment.

### Temporary deployment SSH access

Before approving a release, complete [AWS OIDC setup](AWS-SSH.md). Keep the
administrator and Lightsail browser SSH rules; do not allow GitHub's full runner
range list or public TCP22. The protected job obtains temporary AWS credentials,
recovers a tagged stale /32, opens its own /32 and verifies TCP access. Existing
strict SSH host-key authentication remains mandatory. An `always()` cleanup
closes and verifies the temporary rule after success or failure. Its outcome is
included in the job summary and cleanup failure fails the job.

Forced termination and AWS failures can leave a temporary rule. Recover using
the documented command before retrying; never delete an arbitrary /32 or clear
the marker without closing its rule. AWS session expiry does not expire firewall
rules. Do not run manual recovery during an active deployment. Live OIDC, cleanup
on failure and interrupted-run recovery acceptance remain operator checks.

## 5. First install and later releases

After provisioning and authorization, use a successful main push run or a manual
main dispatch (`gh workflow run ci.yml --ref main`). The workflow validates,
publishes a complete `release-manifest` artifact, then waits for owner approval.
Review the run's full revision, all three digests, launch blockers and maintenance
window before approval. Retain the artifact/run ID as release evidence.

Actions verifies the bundled manifest against the run revision, uploads through
a private incoming directory, activates `/srv/kairos/releases/<revision>`, then
invokes the command below with absolute paths. A retry of an existing revision
requires an identical archive. For an authorized manual invocation, use that
same verified, extracted artifact, not files from the moving main checkout:

```bash
bash "/srv/kairos/releases/${KAIROS_REVISION}/deployment/deploy.sh" \
  "/srv/kairos/releases/${KAIROS_REVISION}" /srv/kairos/production.env
```

The command validates inputs/Compose and external read-only secrets, writes a
release-owned `compose.images.json` override, pulls images, then waits in order
for PostgreSQL/Redis, ZITADEL, a successfully recreated one-shot bootstrap, API,
frontends and recreated NGINX. Bootstrap initializes missing provider credentials
and preserves existing ones. Never start the API first with `--no-deps` on an
empty host. No setup or key generation is part of deployment.

Readiness stages allow 300 seconds each, bootstrap execution 360 seconds and
pulls 900 seconds. The stable project name preserves state; the host lock rejects
concurrent deployments in addition to Actions concurrency. HTTPS probes through
Cloudflare require `200` from both frontend roots and `/api/auth/v1/csrf` with a
nonempty application token. The command writes `deployed-release.json` atomically
only after every check passes. These probes do not prove SSE, RLS, PWA or complete
authentication acceptance. Record first install, reboot recovery and update
acceptance separately in [the launch work](https://github.com/karolbystrek/kairos/issues/19).
Docker and containers with `unless-stopped` must recover on reboot; health checks
alone do not restart unhealthy running processes.

## 6. Failed release and maintenance

A failure reports its stage and safe error metadata in Actions. The previous
success record remains, but the actual stack may be partially updated. Do not
interpret that record as the current state after a failure. Inspect the failed
stage and, only with runtime authorization, container health and bounded logs
privately. Redact credentials, bearer tracking references and personal data before
sharing diagnostics; avoid environment/config dumps and shell tracing.

Fix forward: correct configuration or commit a fix, obtain a new validated
release and approval, and rerun the normal command. There is no automated rollback.
A failed migration requires diagnosis, not removal of volumes, key regeneration,
checksum bypass or resetting production. Back up before destructive migrations
or risky data transformations; application redeployment cannot recover lost data.
At official launch explicitly change the fresh-development schema policy:
applied migrations become immutable and later changes use forward migrations.

| Maintenance | Procedure and verification |
| --- | --- |
| OS/Docker/infrastructure patches | Schedule an outage with the operator; back up when needed, review upstream changes, validate changed Compose/NGINX configuration and deploy a reviewed revision. Infrastructure tags can change independently of source; test upgrades deliberately and record pulled versions. Verify health and reboot recovery. |
| SSH/GHCR credentials | Install and verify replacement credentials through a trusted path before revoking old ones. Update the Environment SSH key/known_hosts after independently verifying any changed host key. Renew host registry credentials before expiry and verify all three pulls without exposing tokens. |
| Provider PATs/admin credentials | Follow [authentication operations](../docs/authentication-setup.md#operations-and-limits). Inventory existing token expirations; bootstrap preserves them. New API PATs use `9999-12-31T23:59:59Z` with the existing service roles; replace the API volume file atomically, restart API, verify auth and revoke the old PAT. The bootstrap administrator PAT is separate and never mounted into the API; rotate it through trusted provider administration. |
| Origin CA certificate | Track expiry with `openssl x509 -in /srv/kairos/tls/tls.crt -noout -dates`. Provision a matching replacement covering all names into the external TLS directory, preserve permissions, recreate/reload NGINX in an approved window, verify Full (strict) paths, then retire the old certificate/key. Never disable strict TLS to hide an expiry failure. |
| Encryption/VAPID keys | Do not overwrite keys in place as a routine rotation. Current encrypted webhook signing secrets and push subscriptions require their original keys; there is no multi-key decrypt/re-encrypt command. Plan a separately reviewed data transformation or explicit retirement of affected data/deliveries, with backup and retention implications. VAPID changes require browser subscription replacement and existing delivery acceptance. Preserve the ZITADEL master key with its database. |
| Security-event retention | Set operational ownership and a deliberate retention policy before launch for invitations, provider/session records, logs and protected backups. Current invitation metadata is retained indefinitely; do not invent a cleanup deadline or delete history without an accepted data-retention change. |

Backups and alerts are [step six](https://github.com/karolbystrek/kairos/issues/18):
confirm daily encrypted off-VM backups, 30-day retention and a 24-hour data-loss
target with the owner; include both databases/roles, keys, provider credential
volumes and protected configuration/certificate material. Rehearse restore on a
replacement host and establish external availability, disk/memory, backup-failure
and certificate-expiry alerts with a named responder and patch schedule. Those
operational targets and checks are pending, not provided by this runbook.

Public launch also requires the independent RLS privilege/isolation work, live
pinned-ZITADEL verification, browser/PWA/push device acceptance and hosted
TLS/firewall/throttling/REST/SSE checks in step seven. Record each as passed,
failed, blocked or not run; documentation and mocked tests do not satisfy them.
