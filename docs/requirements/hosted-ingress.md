# Hosted ingress and Cloudflare range maintenance

### Hosted ingress and Cloudflare range maintenance

The hosted Compose overlay publishes only NGINX port 443 directly on the VM;
application and database ports remain private. Configure Cloudflare to proxy the
single application hostname with Full (strict) encryption. There is no tunnel
service or edge network. The hosted overlay mounts an externally managed Origin
CA certificate directory supplied as absolute `KAIROS_TLS_DIRECTORY`, containing
`tls.crt` and `tls.key`, read-only at `/run/secrets/tls`. Keep certificates and
private keys outside the release checkout. Local development has no gateway or TLS and
publishes loopback-only ports over HTTP. Origin CA certificates are for Cloudflare's
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

Cloudflare must bypass caching for `/api/`, `/panel/` and the customer service
worker script, avoid interactive challenges on `/o/`,
`/api/customer-notifications/` and `/api/external/` so anonymous customers and
External Integrations are never challenged, and avoid broad cache-everything
rules on application pages.
Origin certificate expiry/replacement, edge SSE heartbeat/reconnect behavior,
same-origin `/api` cookies/CSRF, and origin-bypass blocking require operator acceptance.
The isolated gateway test covers TLS/configuration, routing/management rejection,
header replacement/trust, shared throttling, and the SSE buffering response;
it does not establish behavior through the live edge proxy.

### Single-origin path groups

Customers, staff, External Integrations and the metrics collector share one
origin. A nested subdomain per audience would need a paid Cloudflare edge
certificate, so the accepted tradeoff is that anonymous customer pages run on
the same origin as staff session cookies. Same-origin script on any customer
page could therefore read staff pages and call the staff API with a signed-in
staff member's cookies. These mitigations bound that risk:

* Staff cookies keep the `__Host-` prefix and the session cookie stays
  `HttpOnly` and `SameSite=Lax`
  ([staff authentication](staff-authentication.md#browser-cookies-and-durable-sessions)).
  These stop cross-site use and script access to the session ID, not
  same-origin requests, so the measures below carry the isolation.
* The customer manifest and service worker are scoped to `/o/`, so the worker
  never controls or caches `/panel` or `/api` navigations. The worker script is
  served with `no-cache`.
* NGINX sets `Content-Security-Policy` per path group: `/panel/...` allows only
  `'self'` workers, while public and customer pages also allow `blob:` workers
  for the scanner. Every other directive is identical, including
  `object-src 'none'`, `base-uri 'self'` and `form-action 'self'`.
* `Permissions-Policy` grants `camera=(self)` only on `/o/scan`; every other
  path, `/panel` included, sends `camera=()`. The policy belongs to the loaded
  document, so the application must reach `/o/scan` with a full document load.
* HSTS, `nosniff` and `X-Frame-Options: DENY` apply to every path. Locations
  that set their own headers (SSE streams and Prometheus) restate the
  essentials, because NGINX `add_header` does not merge across levels.
