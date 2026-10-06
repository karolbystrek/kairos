# Public deployment design

Date: 2026-10-06
Status: awaiting written-design review; implementation has not started.

## Intent and accepted decisions

Deploy Kairos publicly as one Docker Compose stack on one Linux VM. Keep local
development close to the hosted security and routing configuration, and make
releases repeatable without operating separate application hosting services.

Accepted decisions:

- Cloudflare manages the existing domain's DNS and proxies the three public
  application hostnames. Remove Cloudflare Tunnel.
- NGINX remains the shared gateway for both Next.js frontends and Spring REST/SSE.
- CI builds frontend images with production public URLs; runtime public
  configuration is unnecessary. Credentials and private keys remain outside images.
- GitHub Actions validates, builds, publishes, waits for manual production
  approval, deploys, and verifies health.
- Brief planned outages are acceptable. Failed releases are repaired with a new
  deployment; rollback automation and parallel application stacks are out of scope.
- Backups run on a schedule outside the VM. Additional pre-change backups are
  needed for destructive migrations or risky data transformations, not every release.
- Replace the disposable private-staging runbook with a public-production runbook.

AWS Lightsail is a proposed default, not a dependency of the application design.
The provider, exact VM size, region, hostnames, and credential values are operator
inputs selected during provisioning. Use x86-64 to match the current linux/amd64
images; choose initial capacity with headroom for PostgreSQL, ZITADEL, Java, and
both frontend processes, then measure actual memory and CPU use.

## Architecture and ingress

Browser and external-client traffic follows:

```text
Client -> Cloudflare proxy -> NGINX on VM -> customer-app / panel-app / API
                                                           API -> PostgreSQL
                                                           API -> Redis
                                                           API -> ZITADEL
```

Use three proxied hostnames under one parent domain, for example
customer.example.com, panel.example.com, and api.example.com. They are examples,
not defaults to bake into source. Preserve direct browser calls to the dedicated
API origin, resource-family CORS, secure host-only cookies, CSRF, and exact-host
NGINX routing. Customer access and External Integration APIs require no
Cloudflare Access login or interactive browser challenge.

Cloudflare manages browser-facing certificates. Install an Origin CA certificate
covering the three hostnames in NGINX and use Full (strict) encryption. Mount its
certificate and private key read-only from outside the checkout. Monitor its
expiry and document replacement. Cloudflare Origin CA certificates do not support
direct browser access to NGINX. Production has no Certbot or tunnel container.

Publish only the NGINX web ingress. Restrict VM web ingress to Cloudflare's
published IPv4 and IPv6 ranges; configure NGINX to trust CF-Connecting-IP only
from those proxy ranges and replace upstream forwarding metadata. Maintain those
ranges as an operational update, never trust arbitrary supplied headers.
Administrative access uses a separately restricted SSH path with verified host
keys, key authentication, and no publicly exposed Docker daemon.

Preserve gateway/data network separation, private ZITADEL, internal health
endpoints, rejection of unknown hosts, bounded requests/connections/logs, and
unbuffered SSE. Apply request-rate throttling at NGINX for authentication,
registration, invitation redemption, password changes, and external API access.
Production cannot bypass this gateway through published application ports.

Cloudflare must bypass caching for the complete API hostname and the service
worker script. Do not apply broad cache-everything rules to application pages.
Validate SSE through the proxy, including idle heartbeat behavior and reconnects;
NGINX's long timeout alone does not establish the edge proxy's behavior.

## Local and production parity

Keep one shared Compose topology and small local/hosted overlays. Local builds
use local origins and mkcert; hosted deployments pull CI images and use the
Origin CA certificate. Both use HTTPS at NGINX, the same internal ports, secret
paths, authentication invariants, routing, and throttling policies.

Only hosted ingress trusts Cloudflare forwarding headers. Infrastructure
configuration changes travel with the source revision being deployed. Production
environment files, keys, databases, and certificates live outside the disposable
release checkout and survive application replacement. Local resets remain opt-in.

## Release pipeline

Pull requests validate without publishing or deploying. A main release run
automatically proceeds through validation and publication; the production job
requires explicit approval after all images are available. Preserve manual main
dispatch for deliberate releases/retries. Implementation will configure main
pushes to publish eligible releases; merging is not deployment approval.

Validation includes the current frontend checks/tests/builds and complete Maven
verify lifecycle. Publish all three linux/amd64 application images under the
same full-source-revision tag. Record their registry digests in a release
manifest tied to the workflow run. Partial image sets are not deployable.

The production job targets a protected GitHub Environment with the owner as a
required reviewer, main-only deployment policy, and deployment secrets released
after approval. Allow the owner to approve their own initiated release. Avoid
bypass permissions where configurable. Serialize production jobs without
canceling an in-progress deployment; approval refers to the run's specific
revision and digests, never an evolving latest tag.

Actions connects over SSH using a restricted deployment identity and verified
host key. Production credentials have only the access needed for deployment and
registry pulls. Docker access remains host-privileged; protect that identity
accordingly. Do not install a self-hosted Actions runner on the production VM.

The job runs repository-owned commands against configuration from that exact
revision: validate inputs and Compose, pull images by digest, initialize or check
dependencies including ZITADEL/bootstrap, replace API and frontend containers,
reload/recreate the gateway as required, and await health. First installation
must initialize authentication dependencies before starting the API; the current
runbook's API --no-deps sequence is not a sufficient first-install procedure.

Production URLs are non-secret CI build inputs. Changing them requires a new
source revision and image build. Do not overwrite an existing commit-tagged
image. Private credentials are mounted or supplied only at runtime.

## Failure handling and operations

Stop deployment on invalid configuration, incomplete publication, failed
migrations, or failed health checks. Report the failed stage in Actions and
retain diagnostic logs without credentials. Write the successful deployment
record only after internal and external checks pass. A failed container update
may leave a partially updated stack; ordinary Compose deployment is not atomic.

Use forward fixes. Do not silently reset a database or regenerate keys to make
startup succeed. Flyway changes become forward migrations once a public database
exists; the repository's current never-deployed schema policy must be explicitly
changed at launch. Application fixes cannot recover destructive data changes.

Back up both Kairos and ZITADEL databases, required database roles, the stable
ZITADEL master key, webhook/push encryption keys, VAPID keys, and provider
credential material. Store encrypted backups outside the VM with restricted
access and retention. Initial operating targets are daily backups, 30-day
retention, and up to 24 hours of data loss after host failure; review these with
the owner before official launch. Back up before destructive changes separately.

Rehearse restoration onto a replacement host before launch. Recovery provisions
the host, restores databases and keys consistently, starts the stack, and updates
DNS if needed. Recovery is distinct from application rollback.

Monitor external availability, disk space, memory pressure, backup success,
certificate expiry, and provider/service health. Document OS/Docker/infrastructure
patching, provider credential renewal, key rotation, and security-event retention.
Restart policies recover exited processes; health checks alone do not restart
an unhealthy running process.

## Verification and public-launch prerequisites

Implementation checks must cover changed frontend/backend behavior at its owning
boundary, workflow/Compose configuration, and git diff --check. Do not add tests
for file existence or trivial implementation structure. Runtime acceptance is a
separate explicitly scheduled activity, not authorization to inspect or reset
the currently running local stack.

Required before official launch:

1. Complete verified tenant/location transaction context, PostgreSQL RLS, and
   non-bypass runtime database privileges, with isolation tests.
2. Implement gateway throttling and verify real-client addressing, blocked origin
   bypass, cross-origin cookies/CSRF, hidden management paths, and cache policy.
3. Complete live pinned-ZITADEL acceptance and document credential/key rotation.
4. Complete Android Chrome, iOS/iPadOS Safari, desktop Chrome, and Firefox PWA,
   push, offline, scanner, and subscription replacement acceptance.
5. Verify first install, reboot recovery, approved release, failed deployment,
   external REST/SSE paths, and forward-fix deployment on the selected VM.
6. Restore a backup to a replacement environment and enable operational alerts.
7. Confirm hostnames, capacity, backup targets, and operational ownership; replace
   the staging runbook and remove its production-inappropriate reset instructions.

The implementation plan should separate application/configuration preparation
from operator provisioning and final launch acceptance. No cloud resources,
DNS changes, environment protection settings, secrets, or public deployment have
been created by this design.
