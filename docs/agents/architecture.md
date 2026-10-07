# Architecture and ownership

- Spring Boot owns business rules, authentication, authorization, tenant
  isolation, persistence, browser and external APIs, SSE, Web Push, webhooks,
  and outbox processing.
- Keep REST as the boundary between frontends, External Integrations, and the
  API. Browser families use `/api/{resource-family}/v1`; external families use
  `/api/external/{resource-family}/v1`.
- Local browser-facing API and SSE traffic goes directly to the dedicated API
  HTTPS origin through the shared NGINX gateway, with explicit credentialed
  CORS scoped by frontend origin and browser resource family.
- Frontends use small handwritten request modules and response types with
  native `fetch`. Use SWR for REST-backed client state and Zod for
  frontend-owned input and event validation.
- Do not add Next.js Server Actions or proxy route handlers as an API layer in
  front of Spring.
- Customer SSE and Web Push are invalidation or notification mechanisms; REST
  remains authoritative. Tracked-order REST stays network-only in the service
  worker, and explicit IndexedDB snapshots must be labelled stale when used
  offline.
- Enforce security and tenant access in the API. Frontend redirects and hidden
  controls are user-experience features, not authorization controls.
- Keep authentication credentials unavailable to JavaScript; browser sessions
  use secure HttpOnly cookies, with provider credentials confined to the API.
- Keep production migrations free of environment-specific seed data.
