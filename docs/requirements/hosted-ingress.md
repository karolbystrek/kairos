# Hosted ingress and Cloudflare range maintenance

## Contents

- [Hosted ingress and Cloudflare range maintenance](#hosted-ingress-and-cloudflare-range-maintenance)
- [Contents](#contents)
- [7.2 Hosted ingress and Cloudflare range maintenance](#72-hosted-ingress-and-cloudflare-range-maintenance)

### 7.2 Hosted ingress and Cloudflare range maintenance

The hosted Compose overlay publishes only NGINX port 443 directly on the VM;
application and database ports remain private. Configure Cloudflare to proxy the
customer, panel, and API hostnames with Full (strict) encryption. There is no tunnel
service or edge network. The hosted overlay mounts an externally managed Origin
CA certificate directory supplied as absolute `KAIROS_TLS_DIRECTORY`, containing
`tls.crt` and `tls.key`, read-only at `/run/secrets/tls`. Keep certificates and
private keys outside the release checkout. Local development keeps mkcert and
loopback-only port publication. Origin CA certificates are for Cloudflare's
connection to the origin; direct browser access is not supported.

Only the hosted overlay includes `nginx/cloudflare-real-ip.conf`. It accepts
`CF-Connecting-IP` solely from Cloudflare's published IPv4/IPv6 proxy ranges;
untrusted peers cannot change their rate-limit identity using forwarding
headers. Both overlays replace upstream forwarding metadata with the resolved
client address, exact host, HTTPS scheme and port, and remove the original
Cloudflare and `Forwarded` headers.

Before public operation, restrict VM web ingress to those same Cloudflare ranges
(including Docker-published ports, whose forwarding can bypass ordinary host
firewall rules). Keep SSH separately restricted. Operator firewall provisioning
and real Cloudflare-path acceptance are required launch work, not performed by
repository configuration checks. Never add a broad Docker/VM subnet to the
production trust include merely to make forwarded addresses work.

To maintain the ranges:

1. Retrieve both authoritative lists from
   [Cloudflare IPv4](https://www.cloudflare.com/ips-v4/) and
   [Cloudflare IPv6](https://www.cloudflare.com/ips-v6/). The checked-in snapshot
   was retrieved on 2026-10-07.
2. Review additions/removals; update the `set_real_ip_from` directives and
   retrieval date in `nginx/cloudflare-real-ip.conf` and coordinate the VM
   IPv4/IPv6 ingress allowlist update. Do not trust headers from removed ranges.
3. Run `python3 tests/gateway-test.py` and `git diff --check`, review the change,
   then deploy that revision and recreate NGINX so the bind mount uses the new
   include. Verify real client addresses and blocked direct-origin access.

Cloudflare must bypass caching on the complete API hostname and customer service
worker script, avoid interactive challenges for anonymous customer and External
Integration access, and avoid broad cache-everything rules on application pages.
Origin certificate expiry/replacement, edge SSE heartbeat/reconnect behavior,
cross-origin cookies/CSRF, and origin-bypass blocking require operator acceptance.
The isolated gateway test covers TLS/configuration, routing/management rejection,
header replacement/trust, shared throttling, and the SSE buffering response;
it does not establish behavior through the live edge proxy.
