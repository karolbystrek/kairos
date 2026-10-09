# Public production operations

## Contents

- [Public production operations](#public-production-operations)
- [Contents](#contents)
- [Approval before cloud changes](#approval-before-cloud-changes)
- [Operator sequence](#operator-sequence)
- [Related procedures](#related-procedures)

Kairos runs one Docker Compose stack on an x86-64 Linux VM behind Cloudflare.
Use this runbook for operator provisioning and approved releases. Repository
preparation does not provision a VM, configure DNS or GitHub protection, or
establish public-launch readiness. Those actions require operator inputs and
explicit authorization. The current VPS is a disposable development environment:
all orders, accounts and ZITADEL state may be lost after host failure. Keep
protected configuration outside the host for a fresh rebuild; database recovery
is deferred under the [operations policy](../requirements/routing-releases.md#development-operations-policy).
Use synthetic data until the launch gates in
[issue #9](https://github.com/karolbystrek/kairos/issues/9) are verified.

## Approval before cloud changes

Agents may inspect AWS and Cloudflare read-only. Resource changes require
explicit user approval for the action and scope, including changes made by
scripts or agent-triggered automation. Provisioning instructions are not
permission to execute them. See [cloud operations](../agents/cloud-operations.md).

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

- [PostgreSQL roles and initialization](DATABASE.md): fresh role setup and development schema changes.
- [Ubuntu VM bootstrap](BOOTSTRAP.md): repeatable host packages, account and keys.
- [Native Lightsail email alarms](MONITORING.md): reduced development coverage and activation.
- [AWS OIDC and temporary SSH](AWS-SSH.md): approved firewall lifecycle and recovery.
- [Authentication operations](../authentication-setup.md): provider acceptance and credentials.
- [Requirements topic map](../REQUIREMENTS.md): contracts and outstanding launch gates.
