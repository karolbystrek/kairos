# Cloudflare and origin ingress

Agent execution of AWS/Cloudflare resource changes requires explicit user
approval; read-only inspection is allowed. See [cloud operations](../agents/cloud-operations.md).

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## Configure Cloudflare and origin ingress

Create a proxied DNS record for the application hostname pointing to the VM.
Install a Cloudflare Origin CA certificate covering that name
at `/srv/kairos/tls/tls.crt` and its matching private key at `tls.key`, with the
permissions in [host preparation](HOST.md#choose-and-prepare-the-host). Configure **Full (strict)** encryption. Never use mkcert,
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
[the range maintenance procedure](../requirements/hosted-ingress.md#hosted-ingress-and-cloudflare-range-maintenance).
NGINX trusts `CF-Connecting-IP` only from those ranges and replaces upstream
forwarding headers. Never add arbitrary VM/Docker subnets to restore forwarding.

Set Cloudflare Cache Rules to **Bypass cache** for URI paths starting with
`/api/` or `/panel/` and for the customer service worker script. Do not apply
cache-everything to application pages. Exclude `/o/`,
`/api/customer-notifications/` and `/api/external/` from interactive
challenges/Access login, while retaining the origin gateway limits.
The shared limits are authentication/registration/redemption/password POSTs at
5 requests/minute per client, burst 20, and external API at 10 requests/second,
burst 20, returning `429`. CSRF/read/SSE paths remain outside auth throttling.
See [Cache Rules settings](https://developers.cloudflare.com/cache/how-to/cache-rules/settings/).
Verify edge REST cache bypass, service-worker update behavior, same-origin
`/api` cookies/CSRF and long-lived SSE heartbeats/reconnects on the selected
host.

## Move from three hostnames to one origin

Releases before the single-origin merge used `kairos.karolbystrek.pl` for
customers, `kairos-panel.karolbystrek.pl` for staff and
`kairos-api.karolbystrek.pl` for integrations and metrics. The repository change
performs none of these Cloudflare or DNS steps; each needs explicit operator
approval under [cloud operations](../agents/cloud-operations.md):

1. Before deploying, replace the Cache Rules and challenge exclusions above:
   bypass cache for `/api/` and `/panel/` on `kairos.karolbystrek.pl`, and keep
   `/o/`, `/api/customer-notifications/` and `/api/external/` free of
   challenges. Remove rules that match only the retired hostnames.
2. Deploy the release. NGINX then answers only `kairos.karolbystrek.pl`; the
   old hostnames reach the default server, which closes the connection, and no
   old link is redirected.
3. Delete the `kairos-panel` and `kairos-api` DNS records. The existing Origin
   CA certificate still covers `kairos.karolbystrek.pl`; reissue it for that
   name alone only when it is next replaced.
4. Point the Prometheus scrape target at
   `https://kairos.karolbystrek.pl/api/actuator/prometheus` with the same
   bearer credential ([metrics export](OPERATIONAL_METRICS.md)).
5. Tell External Integration clients their base URL is now
   `https://kairos.karolbystrek.pl/api/external/`; API keys and webhook signing
   secrets are unchanged.
