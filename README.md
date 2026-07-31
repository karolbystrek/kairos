# Kairos

Kairos is a virtual pager system for restaurants. Customers track an order in
the customer PWA, while restaurant staff manage orders in a separate panel.

## Run locally

Install [mkcert](https://github.com/FiloSottile/mkcert) and install its local
certificate authority once:

```bash
mkcert -install
```

Prepare the local environment and its disposable keys and HTTPS certificate:

```bash
./setup.sh
```

Start the production-mode application images built from the working tree:

```bash
docker compose up --build
```

Normal browser traffic enters only through NGINX on loopback HTTPS:

* Customer app: https://customer.kairos.localhost
* Staff panel: https://panel.kairos.localhost
* API: https://api.kairos.localhost

PostgreSQL, Redis, the API, and both frontends do not publish host ports. When
diagnosing a service, inspect it through `docker compose exec`; direct debug
ports are intentionally not part of the maintained topology.

## Compose topology

There are three Compose files in total. `compose.yaml` owns the shared NGINX,
frontend, API, PostgreSQL, and Redis
topology. `compose.local.yaml` adds only local builds, disposable secret mounts,
and loopback HTTPS. `compose.deployment.yaml` replaces local images with
one commit-versioned application release from the configured registry, adds
Cloudflare Tunnel, mounts hosted secrets at the same container paths, and
applies basic resource and log bounds. Infrastructure image versions are owned
directly by the Compose files.

The environment file selects the appropriate pair through `COMPOSE_FILE`.
Local setup uses `compose.yaml:compose.local.yaml`; a private staging or later
production environment uses `compose.yaml:compose.deployment.yaml`. This keeps
ordinary `docker compose` commands identical while leaving structural
differences explicit and reviewable.

The gateway network contains NGINX, both frontends, and the API. The internal
data network contains only the API, PostgreSQL, and Redis. PostgreSQL data is
stored in the `pgdata` volume; Redis Pub/Sub is deliberately nondurable.

## Manual VPS deployment

The first deployment is deliberately operator-initiated. Do not deploy a dirty
tree or use `latest`. Choose an identifiable version, normally the full Git
commit, and build the three application images for the VPS platform:

```bash
test -z "$(git status --porcelain)"
release_version="$(git rev-parse HEAD)"
image_registry="registry.example.com/kairos"

docker buildx build --platform linux/amd64 --push \
  --tag "${image_registry}/api:${release_version}" apps/api
docker buildx build --platform linux/amd64 --push \
  --build-arg NEXT_PUBLIC_API_BASE_URL=https://api.staging.your-domain \
  --tag "${image_registry}/customer-app:${release_version}" apps/customer-app
docker buildx build --platform linux/amd64 --push \
  --build-arg NEXT_PUBLIC_API_BASE_URL=https://api.staging.your-domain \
  --build-arg NEXT_PUBLIC_CUSTOMER_APP_URL=https://customer.staging.your-domain \
  --tag "${image_registry}/panel-app:${release_version}" apps/panel-app
```

Record the registry-resolved `sha256` digest for each application image and
prevent release tags from being overwritten in the registry. On the VPS, create
a restricted environment file from `.env.example`, set
`COMPOSE_FILE=compose.yaml:compose.deployment.yaml`, set
`KAIROS_IMAGE_REGISTRY` to the registry path and `KAIROS_RELEASE_VERSION` to the
same source revision used above, and replace the local origins, credentials,
and identities with staging values. Set absolute host paths for
the application secret directory, TLS secret directory, cloudflared config,
and cloudflared credential file. The application directory must contain the six
key files named in `application.yaml`; the TLS directory must contain
`tls.crt` and `tls.key`. The cloudflared config must refer to
`/run/secrets/cloudflared-credentials.json` and send the three hostname routes
to `http://nginx:80`.

Transfer the matching `compose.yaml`, `compose.deployment.yaml`, and `nginx/`
directory from the same commit to the VPS. Then validate and pull the exact
deployment set:

```bash
deployment_env=/etc/kairos/staging.env
docker compose --env-file "${deployment_env}" config --quiet
docker compose --env-file "${deployment_env}" pull
```

Start the data services first. Replace the API next; its normal startup applies
Flyway migrations before its health check succeeds. Replace the frontends and
gateway only after the migrated API is healthy, then start the tunnel:

```bash
deployment_env=/etc/kairos/staging.env
docker compose --env-file "${deployment_env}" up -d --wait --wait-timeout 180 postgres redis
docker compose --env-file "${deployment_env}" up -d --no-deps --wait --wait-timeout 180 api
docker compose --env-file "${deployment_env}" up -d --no-deps --wait --wait-timeout 180 customer-app panel-app nginx
docker compose --env-file "${deployment_env}" up -d --no-deps cloudflared
```

Finally, verify the recorded image digests and container health, check the
internal NGINX health endpoint, and exercise the three external staging
hostnames through Cloudflare Access:

```bash
deployment_env=/etc/kairos/staging.env
docker compose --env-file "${deployment_env}" images
docker compose --env-file "${deployment_env}" ps
docker compose --env-file "${deployment_env}" exec -T nginx wget -q -O - http://127.0.0.1:8080/health
```

Keep the environment file and deployment record so a rollback selects another
known release version and its recorded image digests rather than rebuilding or
overwriting an old tag.

## Reset the local environment

Run the reset only when discarding the complete local environment is intended:

```bash
./reset.sh
```

The script removes the Compose volumes, local environment, and disposable key
material without asking for confirmation.

Product behavior and architecture are documented in
[`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md).
