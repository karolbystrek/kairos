# Local routing and hosted releases

## Contents

- [Local routing and hosted releases](#local-routing-and-hosted-releases)
- [Contents](#contents)
- [7. Local and Hosted Routing](#7-local-and-hosted-routing)
- [Validation and immutable publication](#validation-and-immutable-publication)
- [OIDC and temporary SSH](#oidc-and-temporary-ssh)
- [Host deployment and health verification](#host-deployment-and-health-verification)
- [Repeatable VM bootstrap](#repeatable-vm-bootstrap)

## 7. Local and Hosted Routing

The local Docker Compose stack uses NGINX HTTPS ingress with mkcert certificates.
Application and data services do not publish host ports. Browser REST and SSE
requests go directly to the dedicated API origin through the gateway.

* The root environment file configures the customer, panel, and API origins and
  exact gateway hostnames; `.env.example` records the configuration surface.
* Spring scopes credentialed CORS by frontend origin and browser resource family.
  External Integration APIs and internal management endpoints have no browser
  CORS policy. Next.js services render frontend concerns only.
* NGINX rejects unknown hosts, keeps frontend and API path spaces separate, and
  exposes Actuator only to container-internal health checks. The API hostname
  forwards `/api/`; frontend hostnames reject that namespace.
* NGINX and applications share the gateway network. Only the API also joins the
  internal data network containing PostgreSQL and Redis.
* Local traffic does not trust supplied forwarding headers. NGINX replaces them
  with a canonical client address, host, HTTPS scheme, and port.
* Both overlays share per-client NGINX request throttling. POST login, password
  change, tenant registration, and invitation redemption share an initial budget
  of 5 requests/minute with burst 5. External `/api/external/` requests use
  10 requests/second with burst 20. Bursts are accepted immediately (`nodelay`);
  excess requests return `429`. CSRF bootstrap, browser reads, preflight, logout,
  invitation preview, and customer SSE do not consume the authentication budget.
  These are initial operational defaults; tune the shared template through a
  reviewed source change. The existing 100 concurrent connections/client bound
  remains in place.
* Request-body, header, connection, and timeout bounds apply. Customer SSE disables
  proxy buffering and uses a read timeout longer than the 30-minute emitter lifetime.
* Local Compose builds application images, mounts private secrets at `/run/secrets`,
  and publishes only NGINX HTTPS on `127.0.0.1`. Redis Pub/Sub is nondurable and
  has no volume; PostgreSQL and provider credential material remain persistent.
* `setup.sh` prepares `.env`, validates the complete key set with explicit handling
  of existing files, preserves the provider master key, and optionally generates
  local TLS material. Secret directories use `0700` and application key files `0400`.

### Validation and immutable publication

GitHub Actions validates pull requests and `main` with frontend lint, type checks,
configured tests and production Docker builds, and the complete Maven `verify`
lifecycle followed by the API Docker build. Frontend
validation requires the non-secret `NEXT_PUBLIC_API_BASE_URL` and
`NEXT_PUBLIC_CUSTOMER_APP_URL` inputs.

Main pushes and manual dispatches on `main` publish all three application images
only after those checks pass; pull requests never publish. Existing full-revision
tags remain unchanged. Every published or reused image is pulled for
`linux/amd64` and inspected for the matching source-revision label and a
repository-owned GHCR registry digest. Validation jobs export OCI image archives
with provenance and upload them with their build digests on main release runs.
One `Publish release` job copies those exact artifacts to GHCR using Skopeo with
all manifests and digest preservation, checks published digests against build
outputs, captures inspections locally, and verifies all three before uploading
the `release-manifest` artifact. Publication never rebuilds the images and is
serialized for the same revision. Previously published immutable tags remain
unchanged and are inspected before reuse. PRs build images without publishing.
Failed or incomplete publication cannot produce an eligible release.

That artifact contains `release.json` with a full 40-character lowercase Git
`revision` and `images` entries for `customer-app`, `panel-app`, and `api`, each
using `ghcr.io/karolbystrek/kairos/<service>@sha256:<64 lowercase hex>`.
`release.tar.gz` contains the same manifest plus the exact checkout's shared and
hosted Compose files, both NGINX configuration files, and the ZITADEL bootstrap
script and the deployment shell command/Python helper. An explicit file allowlist
excludes environment files, certificates and private keys. A main-only
production job waits for successful publication and targets the
`production` GitHub Environment. It downloads this run's artifact, validates
its manifest/archive against the workflow revision, and transfers it over SSH
with the supplied verified host keys and ephemeral `0700`/`0600` SSH files.
The four Environment secrets are `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`,
and `DEPLOY_KNOWN_HOSTS`; publication jobs receive none of these secrets.

### OIDC and temporary SSH

The protected production job alone has `id-token: write` and obtains AWS
credentials through GitHub OIDC for 7200 seconds, exceeding its 70-minute timeout.
IAM trust matches this repository's exact production Environment subject and
`sts.amazonaws.com` audience. Environment policy still restricts deployment to
main. The role permits firewall changes only on the selected Lightsail instance
and tag changes only for `kairos-deploy-ssh`; read APIs require region-limited
`Resource: *`. Lightsail cannot restrict these write permissions to a particular
port, so workflow code and approval are part of this authority boundary.

`deployment/ssh_firewall.py` records the run identity and validated public runner
IPv4 /32 before opening TCP22. Before opening, it recovers only a tagged stale
rule, protects explicit administrator CIDRs and refuses existing unmarked access.
It preserves browser SSH and HTTPS rules. The same runner verifies TCP readiness
before the existing host-key-verified SSH transfer. Cleanup runs with `always()`,
verifies the recorded rule is absent and only then removes its tag; cleanup
failure fails the job and leaves recoverable state. A different run cannot clean
an active owner's rule. Forced termination or AWS outages can leave access until
the next run or explicit operator recovery; rules do not expire with credentials.
Changing permanent administrator CIDRs requires updating `DEPLOY_ADMIN_CIDRS`
before reusing an address that might still be tracked by a deployment marker.

### Host deployment and health verification

Deployment jobs use `kairos-production` concurrency without canceling an active
job. The upload activates the complete bundle at `/srv/kairos/releases/<revision>`
from a private temporary directory, retaining existing revision directories;
a retry requires an identical archive. It invokes the bundled host command with
`/srv/kairos/production.env` and retains the host deployment lock. Actions reports
the validated revision, three digests and deployment result without credentials.
The host needs GNU `tar`, `mv`, and `cmp` alongside the deployment prerequisites.
Required reviewer/main-only Environment protection and operator secrets must
be provisioned before releases are allowed; the workflow cannot install
those protections. The owner may approve their own initiated runs
(`prevent_self_review=false`), and administrator bypass remains allowed
(`can_admins_bypass=true`) under the owner-approved policy. Live approval/withheld-approval and deployment acceptance
remain operator launch checks.

`deployment/deploy.sh RELEASE_DIRECTORY ENV_FILE` runs on the Linux VM against
an extracted exact-revision bundle. Both arguments are absolute; the environment
file and existing application keys/Origin CA certificate material remain outside
the release directory. The host needs Python 3.9 or newer, Docker with Compose
supporting `up --wait --wait-timeout` and `config --no-path-resolution`, curl
with `--max-filesize`, registry pull
credentials, and a deployment identity able to use Docker and write the release
and `/srv/kairos` directories. No setup command or key generator runs during
deployment.

The command validates the complete repository-owned digest manifest and resolved
hosted Compose configuration before container mutation. A release-owned JSON
Compose override pins all three application images to their recorded digests.
The Compose project is always `kairos`, preserving named volumes across release
directories. A nonblocking host-wide `flock` at `/srv/kairos/deploy.lock` rejects
concurrent deployments; Python's stdlib `fcntl.flock` provides the lock without
an additional host command dependency.

Deployment pulls the complete stack, waits for PostgreSQL/Redis health, then
ZITADEL health, and completes a recreated one-shot bootstrap before starting the
API. The bootstrap preserves existing provider credentials. Only after API health
passes does it replace the frontends and recreate NGINX so configuration bind
mounts follow the deployed revision. Each readiness stage has a 300-second bound;
bootstrap execution has a 360-second bound and image pulls a 900-second bound.
Compose's internal health checks include the actual API Actuator endpoint.

Public HTTPS probes require `200` from both frontend roots and the API CSRF
endpoint, including a nonempty application CSRF token. Probes use normal TLS
verification, direct DNS-routed requests without local proxies or redirects, and
at most five attempts
per endpoint with 5-second connect and 15-second request limits. Only after every
check passes does the command atomically replace `/srv/kairos/deployed-release.json`
with the successful revision/digests. Failed stages retain the previous success
record and report the failed stage with a safe validation reason, command exit
status/timeout, or final probe HTTP status. Raw command arguments/output, response
bodies, environment values and resolved configuration are not printed. A failed
update can leave a partially updated stack; recovery uses a forward fix, with no automated rollback or reset.
Command tests use fake Docker/curl and temporary state. First install, reboot
recovery and real Cloudflare-path acceptance on the selected VM remain launch
work requiring explicit runtime authorization.

### Repeatable VM bootstrap

The operator can prepare an Ubuntu 24.04 x86-64 VM with
`deployment/bootstrap.yml` from a trusted workstation over verified SSH. The
playbook installs Docker Engine/Compose from Docker's official apt repository
and the host tools, enables Docker on boot, creates the dedicated deployment
`kairos-deploy` identity with a restricted SSH key, and prepares private `/srv/kairos` paths.
It keeps the existing `ubuntu` administrator unchanged and rejects selecting
`ubuntu` as the deployment identity.
It refuses unsupported hosts, invalid public keys and conflicting Docker packages
rather than removing existing infrastructure. Installed packages are retained on
reruns; upgrades remain deliberately scheduled maintenance.

Host preparation reuses this checkout's `setup.sh` with `--keep-env --keep-keys
--no-tls`, preserves existing production configuration and key bytes, rejects
invalid/partial key sets, and removes temporary setup inputs even on failure.
Bootstrap publishes `/srv/kairos/production.env.example` from repository-owned
`deployment/production.env.example`, with production hostnames, persistent paths
and public-HTTPS destination policies. Reruns refresh only this non-secret
example. Passwords, operator contact and bootstrap PAT expiry are blank for
operator input; the live `production.env` is never created or overwritten by
bootstrap. The local `.env.example` remains unchanged.
Bootstrap does not run `reset.sh`, start applications, configure provider/network
firewalls, DNS, TLS or GitHub Environment settings. Those remain separate operator
steps. See [VM bootstrap](../deployment/BOOTSTRAP.md); actual host and repeat-run
acceptance remain operator verification.
