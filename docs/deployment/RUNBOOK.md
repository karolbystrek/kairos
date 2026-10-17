# Public production operations

Kairos runs one Docker Compose stack on an x86-64 Linux VM behind Cloudflare.
Use this runbook for operator provisioning and approved releases. Repository
preparation does not provision a VM, configure DNS or GitHub protection, or
establish public-launch readiness. Those actions require operator inputs and
explicit authorization. The current VPS is a disposable development environment:
all orders, accounts and credentials may be lost after host failure. Runtime
configuration lives in the GitHub `production` Environment; keep keys and TLS
material outside the host for a fresh rebuild. Nightly on-host database dumps
exist, but off-host recovery is a launch gate under the
[operations policy](../requirements/routing-releases.md#development-operations-policy).
Use synthetic data until the launch gates in
[issue #9](https://github.com/karolbystrek/kairos/issues/9) are verified.

Cloud changes need approval: see [cloud operations](../agents/cloud-operations.md).

## Operator sequence

Follow these guides in order for a fresh host; for maintenance, open the relevant
step and check its prerequisites. Public launch still requires every launch gate.

| Step | Guide | Use for |
| --- | --- | --- |
| 1 | [Host preparation](HOST.md) | Production step 1 |
| 2 | [Persistent configuration and keys](CONFIGURATION.md) | Production step 2 |
| 3 | [Cloudflare and origin ingress](INGRESS.md) | Production step 3 |
| 4 | [GitHub release protection](GITHUB.md) | Production step 4 |
| 5 | [First install and later releases](RELEASES.md) | Production step 5 |
| 6 | [Failed release and maintenance](MAINTENANCE.md) | Production step 6 |

## Related procedures

- [PostgreSQL roles and initialization](DATABASE.md): fresh role setup, [pre-launch baseline reset](DATABASE.md#transition-from-the-old-development-database) and [platform administrator grants](DATABASE.md#platform-administrator-grants).
- [Performance benchmark](PERFORMANCE.md): synthetic load scenarios, measurements and provisional acceptance budgets.
- [Ubuntu VM bootstrap](BOOTSTRAP.md): repeatable host packages, account and keys.
- [Native Lightsail email alarms](MONITORING.md): reduced development coverage and activation.
- [AWS OIDC and temporary SSH](AWS-SSH.md): approved firewall lifecycle and recovery.
- [Requirements topic map](../REQUIREMENTS.md): contracts and outstanding launch gates.
