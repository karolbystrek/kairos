# API operational metrics export

## Enable export

The API exposes GET `/api/actuator/prometheus`, protected by its own bearer token.
Export stays inaccessible until an operator mounts a valid token file. Staff
sessions, API keys and URL/query credentials do not authorize export. Existing
public health probes continue unchanged. No collector is installed by this code.

Generate 32 random bytes encoded as unpadded base64url in a private file, without
printing the token or committing it. The file accepts 43–128 base64url characters
and an optional final newline. For example, on the operator's trusted host:

```sh
umask 077
python3 - <<'PY'
import secrets
from pathlib import Path
Path('metrics-token').write_text(secrets.token_urlsafe(32) + '\n')
PY
```

Mount that file read-only at `/run/secrets/metrics-token` in the API container,
using an operator-owned Compose override. Ensure the container user can read it.
The default Compose stack needs no token and no new management port. For a
nondefault path set `KAIROS_MONITORING_TOKEN_LOCATION=file:/path/to/token` in the
API environment. The token is loaded at startup; rotate the file and recreate
only the API through the approved release process, then update the collector.
An invalid/unreadable file fails closed. A deployment action remains separately
authorized; these instructions do not authorize live resource changes.

## Collector configuration

Use a trusted collector over HTTPS through the API origin, with path
`/api/actuator/prometheus` and an Authorization bearer token loaded from the
collector's secret file. Do not put credentials in URLs, source control, logs,
command arguments or labels. On a trusted private container network, direct API
access is possible under the same authentication. Keep that network private.

A future Prometheus job can use `authorization.credentials_file` and a 30-second
scrape interval. Collector installation, retained history, Grafana and alert
configuration are deferred. Establish retention and access control when that
separate increment is approved.

## Interpretation and verification

See [metric definitions and limits](../requirements/operational-metrics.md).
Prometheus converts dotted meter names to underscore names, durations to seconds
and counters to `_total` series. Backlog gauges have only a fixed `worker` label;
check `kairos_worker_backlog_snapshot_up` and
`kairos_worker_backlog_snapshot_timestamp_seconds` before interpreting them.
Before initial refresh, backlog is unknown rather than zero.

Operational counters reset with API restarts, and delivery attempts are
approximate transport measurements. Use durable business aggregates for order
counts. Connection-pool meters show pool pressure; health probes indicate
reachability, without exposing database details to anonymous callers.

Focused repository checks cover unauthorized/wrong/missing credentials,
management endpoint isolation, bounded labels, representative delivery/worker
instrumentation and restricted-role backlog snapshots. Live HTTPS export,
credential mounting and collector retention need operator acceptance when a
collector is connected. This increment makes no live infrastructure changes.
