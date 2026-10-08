# Kairos Architecture and Requirements

## Contents

- [Kairos Architecture and Requirements](#kairos-architecture-and-requirements)
- [Contents](#contents)
- [Reading by task](#reading-by-task)
- [Topic map](#topic-map)
- [Common dependencies](#common-dependencies)
- [Maintaining requirements](#maintaining-requirements)

Canonical requirements live in the topic files below. This index is the entry
point; the linked files form one specification, not independent alternatives.

## Reading by task

Before planning, implementing or reviewing, read the purpose/ownership and
current delivery status, then the topics affected by the task. Use each file's
Contents to select relevant sections. Follow cross-links and read adjacent
security, data-integrity and contract rules when the change crosses boundaries.
Do not skip an applicable requirement because it lives in another file.

## Topic map

| Topic | When to read |
| --- | --- |
| [Purpose, technology and ownership](requirements/overview-technology.md) | All work: scope, stack and ownership; frontend visual conventions |
| [Order lifecycle and customer application](requirements/orders-customer.md) | Order changes, customer tracking, PWA, offline and notifications |
| [Staff panel requirements](requirements/staff-panel.md) | Staff queues, navigation, management UI and confirmations |
| [External Integration API and webhooks](requirements/external-integrations.md) | Integration credentials, external orders and webhook delivery |
| [Staff authentication and browser sessions](requirements/staff-authentication.md) | Registration, onboarding, login, sessions, CSRF and provider validation |
| [Location lifecycle and cascades](requirements/locations.md) | Location disable, enable and irreversible Delete |
| [Accounts and invitations](requirements/accounts-invitations.md) | Member provisioning, invitation redemption, account management and access |
| [Provider setup and registration consistency](requirements/provider.md) | ZITADEL provisioning, service credentials and registration cleanup |
| [Customer, integration and tenant access](requirements/access-isolation.md) | Any authorization, tenant isolation, RLS or customer capability change |
| [Persistence requirements](requirements/persistence.md) | Schema, migrations, ownership, secret storage and automatic order labels |
| [Real-time communication](requirements/real-time.md) | Customer SSE, Redis Pub/Sub and Web Push invalidation |
| [Local routing and hosted releases](requirements/routing-releases.md) | Compose, gateway, CI publication, deployment and VM bootstrap |
| [HTTP resource families and contracts](requirements/http-contracts.md) | API endpoints, response shapes, lifecycle operations and HTTP behavior |
| [Hosted ingress and Cloudflare range maintenance](requirements/hosted-ingress.md) | Cloudflare, public TLS, trusted proxies, firewall and cache acceptance |
| [Resilience and consistency](requirements/resilience.md) | Transactions, idempotency, delivery retry, freshness and retention |
| [Verification and acceptance criteria](requirements/acceptance.md) | Selecting product, security, browser and deployment acceptance |
| [Current delivery status and roadmap](requirements/delivery-status.md) | All work: implemented scope, outstanding launch gates and deferred work |

## Common dependencies

Read these alongside the primary topic whenever the task crosses its boundary:

| Change | Also read |
| --- | --- |
| Staff or integration authorization | [Access/isolation](requirements/access-isolation.md), [sessions](requirements/staff-authentication.md), [HTTP contracts](requirements/http-contracts.md) |
| Schema or lifecycle | [Persistence](requirements/persistence.md), [locations](requirements/locations.md), [accounts/invitations](requirements/accounts-invitations.md), [resilience](requirements/resilience.md) |
| Customer tracking or notifications | [Customer requirements](requirements/orders-customer.md), [access](requirements/access-isolation.md), [real-time](requirements/real-time.md), [resilience](requirements/resilience.md) |
| Gateway, deployment or cloud | [Routing/releases](requirements/routing-releases.md), [hosted ingress](requirements/hosted-ingress.md), [production runbook](../deployment/RUNBOOK.md), [cloud approval](agents/cloud-operations.md) |
| Verification of any changed behavior | [Acceptance](requirements/acceptance.md) and [validation guidance](agents/validation.md) |

## Maintaining requirements

Record accepted product, architecture, security, ownership and contract
changes in their canonical topic file. Update this map when adding or moving a
topic. Preserve requirements when splitting files; do not copy the same rules
into multiple sources. Follow [documentation maintenance](agents/documentation.md).
