# Cloudflare and origin ingress

## Contents

- [Cloudflare and origin ingress](#cloudflare-and-origin-ingress)
- [Contents](#contents)
- [3. Configure Cloudflare and origin ingress](#3-configure-cloudflare-and-origin-ingress)

Agent execution of AWS/Cloudflare resource changes requires explicit user
approval; read-only inspection is allowed. See [cloud operations](../agents/cloud-operations.md).

Use [the production runbook](RUNBOOK.md) for prerequisites and the sequence.

## 3. Configure Cloudflare and origin ingress

Create proxied DNS records for the three chosen application hostnames pointing
to the VM. Install a Cloudflare Origin CA certificate covering all three names
at `/srv/kairos/tls/tls.crt` and its matching private key at `tls.key`, with the
permissions in [host preparation](HOST.md#1-choose-and-prepare-the-host). Configure **Full (strict)** encryption. Never use mkcert,
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
[the range maintenance procedure](../requirements/hosted-ingress.md#72-hosted-ingress-and-cloudflare-range-maintenance).
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
