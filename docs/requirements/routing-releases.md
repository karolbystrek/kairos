# Local routing and hosted releases

## Contents

- [Local routing and hosted releases](#local-routing-and-hosted-releases)
- [Contents](#contents)
- [7. Local and Hosted Routing](#7-local-and-hosted-routing)
- [Validation and immutable publication](#validation-and-immutable-publication)
- [OIDC and temporary SSH](#oidc-and-temporary-ssh)
- [Host deployment and health verification](#host-deployment-and-health-verification)
- [Repeatable VM bootstrap](#repeatable-vm-bootstrap)
- [Development operations policy](#development-operations-policy)

## 7. Local and Hosted Routing

Production Compose (`compose.yaml` plus `compose.production.yaml`) uses NGINX
HTTPS ingress. Application and data services do not publish host ports. Browser
REST and SSE requests go directly to the dedicated API origin through the gateway.
Local development has no gateway: `compose.yaml` publishes infrastructure
ports on `127.0.0.1`, the applications run on the host over HTTP, and browsers
call the API origin `http://localhost:8080` directly.

* The root environment file configures the customer, panel, and API origins;
  production adds exact gateway hostnames. `.env.example` records the local
  configuration surface and `deployment/production.env.example` the hosted one.
* Spring scopes credentialed CORS by frontend origin and browser resource family.
  External Integration APIs and internal management endpoints have no browser
  CORS policy. Next.js services render frontend concerns only.
* NGINX rejects unknown hosts, keeps frontend and API path spaces separate, and
  forwards only the dedicated bearer-protected Prometheus Actuator resource;
  other management resources stay limited to container-internal health checks. The API hostname
  forwards `/api/`; frontend hostnames reject that namespace.
* NGINX and applications share the gateway network. Only the API also joins the
  internal data network containing PostgreSQL and Redis.
* NGINX does not trust supplied forwarding headers, except the Cloudflare client
  address from trusted ranges. It replaces them with a canonical client address,
  host, HTTPS scheme, and port.
* NGINX applies per-client request throttling. POST login, password
  change, email verification/code resend, tenant registration, and invitation redemption share an initial budget
  of 5 requests/minute with burst 20. External `/api/external/` requests use
  10 requests/second with burst 20. Bursts are accepted immediately (`nodelay`);
  excess requests return `429`. CSRF bootstrap, browser reads, preflight, logout,
  invitation preview, and customer and staff SSE do not consume the authentication budget.
  These are initial operational defaults; tune the shared template through a
  reviewed source change. The existing 100 concurrent connections/client bound
  remains in place.
* Request-body, header, connection, and timeout bounds apply. Customer and staff SSE disable
  proxy buffering and use a read timeout longer than the 30-minute emitter lifetime.
* The customer and panel hosts add HSTS, `X-Content-Type-Options: nosniff` and
  `X-Frame-Options: DENY`; the API sends the same through Spring Security.
* `compose.yaml` holds only PostgreSQL and Redis, published on `127.0.0.1`.
  Locally the applications run on the host with hot reload (`./mvnw
  spring-boot:run` with Spring Boot DevTools, `next dev`) and read the same
  configuration keys as production: the API imports the repository `.env`, the
  frontends their committed `.env.development`. `compose.production.yaml` adds the
  application images, NGINX, private networking and limits. Rate limits, header
  replacement and SSE proxy settings are therefore exercised only by the
  production overlay and `tests/gateway-test.py`. Redis Pub/Sub is nondurable and
  has no volume; PostgreSQL data remains persistent.
* `setup.sh` validates the complete key set with explicit handling of existing
  files. Without a preset it asks about each step; `--dev` prepares `.env` and
  `secrets/` without prompts, installs frontend dependencies and starts the
  infrastructure, and `--prod` prepares `/srv/kairos/secrets` without writing an
  environment file. Secret directories use `0700` and application key files
  `0400`. `reset.sh` has no modes: it removes the Compose project, its volumes and
  the generated keys in the configured secrets directory.

### Validation and immutable publication

GitHub Actions validates pull requests and `main` with frontend lint, type checks,
configured tests and production `npm run build`, and the complete Maven `verify`
lifecycle. CI builds no images. Frontend validation requires the non-secret
`NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_CUSTOMER_APP_URL` and
`NEXT_PUBLIC_PANEL_APP_URL` inputs. Main CI keeps dependency caches warm for
pull requests.

Only the manual production deployment builds and publishes images. It first runs
the full CI workflow for the dispatch revision; failed validation stops before
any image is built. Each image is then built with provenance for `linux/amd64`
and pushed to GHCR under its full-revision tag, serialized per revision and
service. An existing full-revision tag is reused unchanged and never rebuilt,
because a rebuild changes digests. The `Publish release` job pulls and inspects
all three images for the matching source-revision label and a repository-owned
GHCR registry digest before uploading the `release-manifest` artifact. Failed or
incomplete publication cannot produce an eligible release.

That artifact contains `release.json` with a full 40-character lowercase Git
`revision` and `images` entries for `customer-app`, `panel-app`, and `api`, each
using `ghcr.io/karolbystrek/kairos/<service>@sha256:<64 lowercase hex>`.
`release.tar.gz` contains the same manifest plus the exact checkout's shared and
hosted Compose files, both NGINX configuration files, the PostgreSQL entrypoint
role initialization script,
exact SQL migrations, and the deployment shell command/Python helper. An explicit file allowlist
excludes environment files, certificates and private keys. The separate
`deploy.yml` workflow runs only by manual dispatch on `main`, with no revision
input; every job refuses other refs. It uses the main revision captured at
dispatch, which remains fixed if `main` advances afterward.

The production job targets the `production` GitHub Environment, downloads its
own workflow's release artifact, validates its manifest/archive against the dispatch
revision, and transfers it over SSH
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
Main-only Environment protection and operator secrets must be provisioned
before releases are allowed; the workflow cannot install those protections.
Manually dispatching the workflow is the production approval; the Environment
has no separate required reviewer. Deployment acceptance remains an operator
launch check.

`python3 deployment/deploy.py RELEASE_DIRECTORY ENV_FILE` runs on the Linux VM against
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

Deployment pulls the complete stack and waits for PostgreSQL/Redis health before
starting the API. Fresh PostgreSQL initializes the restricted database roles
through its standard entrypoint; Spring Flyway applies migrations automatically. Existing database volumes do
not rerun initialization; see [database initialization](../deployment/DATABASE.md).
Ordinary deployment never resets volumes or repairs Flyway history. Only after API health
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

Host preparation reuses this checkout's `setup.sh --prod`, preserves existing key bytes, rejects invalid/partial key sets, and
removes temporary setup inputs even on failure. Bootstrap never creates or
changes `production.env`. The GitHub `production` Environment owns production
configuration: each deployment writes `/srv/kairos/production.env` atomically
(mode `0600`) from repository-owned `deployment/production.env.example` plus
Environment secrets/variables that the deploy step names explicitly (never
`toJSON(secrets)`). Template defaults need no GitHub value; blank template
settings are required, and a change adding one must also add its Environment
value and deploy-step mapping. A missing or unsafe value fails before SSH,
naming keys only. Values reach the host only
over the deploy job's SSH connection, never images, bundles or artifacts; image
build/publish jobs have no Environment access. The local `.env.example` remains
unchanged.
Bootstrap does not run `reset.sh`, start applications, configure provider/network
firewalls, DNS, TLS or GitHub Environment settings. Those remain separate operator
steps. See [VM bootstrap](../deployment/BOOTSTRAP.md); actual host and repeat-run
acceptance remain operator verification.

### Development operations policy

The owner accepts the public single-VM deployment as a disposable development
environment with no users. All orders, accounts and credentials may be lost
after host failure and recreated. Scheduled database backups, off-host backup
storage/encryption/retention, destructive-migration backup procedures, data-loss
and recovery targets, and replacement-host restore rehearsals are deferred.
Runtime configuration lives in the GitHub `production` Environment; keep
protected application keys and TLS material outside the VPS to rebuild a fresh
installation. This is not database restoration.
Acceptance of data loss does not authorize automatic deletion, resets or key
regeneration; existing runtime approval and data-safety rules still apply.

Current alerting uses AWS-native Lightsail email alarms for failed instance/system
status checks, sustained high CPU and low remaining CPU burst capacity, plus
GitHub Actions failed-workflow email notifications. Karol Bystrek is the responder;
the owner selects and verifies the private email destination.
[Alarm setup](../deployment/MONITORING.md) defines thresholds, provisioning,
notification tests and pending activation. Disk-space, memory-pressure and public
HTTPS availability alerts are explicitly deferred. No custom host scripts,
monitoring agents, additional monitoring stack or external service are required.
A healthy instance can still have an application outage. Certificate expiry
remains an operator maintenance check.

Before onboarding real users or retaining valuable data, agree and implement
backups for the Kairos database, roles and matching encryption/signing keys; define retention and recovery targets
and verify replacement-host restoration. Reassess application and capacity
monitoring coverage then. Separate database/Redis instances require their own
plan and do not themselves establish recoverability. Independent security,
authentication, device and live deployment acceptance gates remain required
before public launch.
