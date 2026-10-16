# Architecture and ownership

## Contents

- [Architecture and ownership](#architecture-and-ownership)
- [Contents](#contents)
- [Ownership and REST boundaries](#ownership-and-rest-boundaries)
- [Real-time and security invariants](#real-time-and-security-invariants)

## Ownership and REST boundaries

- Spring Boot owns business rules, authentication, authorization, tenant
  isolation, persistence, browser and external APIs, SSE, Web Push, webhooks,
  and outbox processing.
- Keep REST as the boundary between frontends, External Integrations, and the
  API. Browser families use `/api/{resource-family}/v1`; external families use
  `/api/external/{resource-family}/v1`.
- Browser-facing API and SSE traffic goes directly to the dedicated API origin
  (through NGINX HTTPS in production, `http://localhost:8080` locally), with
  explicit credentialed CORS scoped by frontend origin and browser resource family.
- Frontends use small handwritten request modules and response types with
  native `fetch`. Use SWR for REST-backed client state and Zod for
  frontend-owned input and event validation.
- Do not add Next.js Server Actions or proxy route handlers as an API layer in
  front of Spring.
## Real-time and security invariants

- PostgreSQL remains authoritative; customer SSE carries customer state and
  staff SSE carries authorized active queues over one Redis order-change topic.
  Customer REST, SSE and Web Push reconcile monotonically by status. Streams
  deliver snapshots on connect and Redis resubscription; staff access is checked
  at least every five minutes. Tracked-order REST stays network-only in the
  service worker, and offline IndexedDB snapshots must be labelled stale.
- Push and webhook workers wake asynchronously after commit, fan out before
  delivery, and retain PostgreSQL claims with five-second recovery polling.
- Enforce security and tenant access in the API. Frontend redirects and hidden
  controls are user-experience features, not authorization controls.
- Keep authentication credentials unavailable to JavaScript; browser sessions
  use secure HttpOnly cookies, with password hashes confined to the API and database.
- Keep production migrations free of environment-specific seed data.
- A change adding a required (blank) key to `deployment/production.env.example`
  must also map it in the `deploy.yml` deploy step and tell the user to set it in
  the GitHub `production` Environment before merging; see
  [configuration](../deployment/CONFIGURATION.md).
