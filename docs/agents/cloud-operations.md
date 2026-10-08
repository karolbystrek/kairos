# Cloud operations and approval

## Contents

- [Cloud operations and approval](#cloud-operations-and-approval)
- [Contents](#contents)
- [Read-only access](#read-only-access)
- [Resource changes](#resource-changes)
- [Approval scope](#approval-scope)

## Read-only access

Agents may use AWS CLI and Cloudflare CLI/API tools to read service state
without additional approval. Examples include listing Lightsail instances,
reading firewall rules or IAM policies, and inspecting Cloudflare DNS records,
certificate status or cache rules. Use the least access needed, and keep
credentials, tokens and private configuration out of tool output and Git.

A command is read-only only when it does not change remote resources. Inspect
an unfamiliar command or script before running it; a name such as check,
preview or plan is not proof that it cannot write.

## Resource changes

Never perform an AWS or Cloudflare resource-modifying action without explicit
user approval for the action and scope. This applies to CLI commands, APIs,
console interactions, scripts, infrastructure tools and automation triggered
by the agent.

Changes include creating, updating or deleting resources; changing firewall,
IAM, DNS, TLS or caching configuration; rebooting or stopping instances;
purging caches; and starting deployment or infrastructure workflows that
modify these services. Permission to edit deployment code, inspect state or
use credentials alone does not authorize these live actions.

Prepare a concrete, reviewable change first. Explain the target, intended
effect and material risk when requesting approval. Do not execute the change
while approval is pending. Read-only checks may continue.

## Approval scope

Explicit approval already given in the conversation remains valid for the
stated action and scope; do not repeatedly ask for the same approval. Ask
again when the target, action or scope changes or the existing authorization
is unclear. Broad implementation requests do not imply approval to modify
live cloud resources.

After an approved change, verify the result with read-only checks and report
what changed. Approval for one service does not automatically cover another.
Human approval of a production deployment may cover its documented temporary
firewall open/deploy/cleanup lifecycle; agent-triggered execution still needs
explicit authorization for that lifecycle.
