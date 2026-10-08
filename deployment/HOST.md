# Host preparation

## Contents

- [Host preparation](#host-preparation)
- [Contents](#contents)
- [1. Choose and prepare the host](#1-choose-and-prepare-the-host)

Agent execution of AWS/Cloudflare resource changes requires explicit user
approval; read-only inspection is allowed. See [cloud operations](../docs/agents/cloud-operations.md).

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

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
