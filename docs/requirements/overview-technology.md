# Purpose, technology and ownership

## Purpose and scope

Kairos is a multi-tenant virtual pager system for restaurants. A tenant represents a customer organization, such as an independent restaurant or restaurant chain, and owns physical locations. Public registration first creates the tenant and administrator; before entering the staff workspace, that administrator must create the first enabled location through mandatory onboarding. A tenant may temporarily have no location while onboarding is incomplete. Once created, its last non-archived location may be disabled but cannot be deleted. A customer scans a QR code assigned to an order and opens a lightweight web application that displays the current order state and receives real-time updates. Restaurant staff manage orders through a separate administrative panel. External systems, initially point-of-sale systems, can create and update orders through a versioned REST API and receive webhooks.

Kairos replaces failure-prone physical restaurant pagers without requiring a
customer to install a chain-specific native application for a short-lived
transaction. It remains independently usable by restaurant staff while
offering an optional, language-agnostic integration boundary for point-of-sale
systems.

The core system consists of three applications:

* **Customer application:** anonymous, mobile-first Next.js PWA for order tracking.
* **Staff panel:** authenticated Next.js application for queue management and QR-code generation.
* **API:** Spring Boot application responsible for all business rules, security, persistence, real-time communication, and external integrations.

Native mobile applications, App Clips, and Instant Apps may be added later, but
the browser experience must work without installing the customer PWA. Web Push
is an optional enhancement that requires explicit customer consent; foreground
REST and SSE tracking remains the primary experience.

## Technology and Ownership Decisions

### Frontend applications

Both frontends use Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI 3,
and Phosphor Icons. They remain separate because they serve different audiences
and have different authentication, PWA, caching, and release concerns. The
customer application uses a custom TypeScript service worker built with Serwist
in configurator mode and owns its offline snapshots in IndexedDB.

Both frontends share one coherent, Apple-inspired web design language while
retaining browser-native behavior. Differences in information density reflect
the customer and staff tasks rather than different brand or role-specific
styling. The **Kairos** name is the only visual-brand artifact that must be
preserved during the redesign; icons, colors, typography and other visual
assets may be replaced. The visual system itself lives in each application's
shared stylesheet and components rather than in these requirements.

#### Appearance and popup behavior

The visual character is direct, neutral and hospitality-oriented without
restaurant-themed decoration. Hierarchy comes from typography, alignment,
spacing and separators rather than decorative containers; color is reserved for
meaningful actions, selection, status and errors, and icons are kept only when
they aid navigation, actions or status recognition.

Popups use a clear title and direct actions. Concise instructions are retained
only for non-obvious setup, irreversible effects, access/account cascades,
recovery and one-time secrets.

Optional popups close through Close, an outside click or Escape, including
while a request is pending; closing does not cancel an already submitted
operation or permit duplicate submission. Queue location selection is
temporarily unavailable while an order mutation is pending, so completion stays
associated with the submitted queue. Required steps such as first-location
onboarding use a non-dismissible required mode that rejects every dismissal.
Focus management and focus restoration are retained. Dismissing invitation
creation while its request is pending does not cancel accepted email delivery.
Success revalidates invitation history; no link or once-revealed secret is
displayed in the panel.

Both frontends provide one Dark mode toggle. Until it is first pressed, the
appearance follows the browser's color-scheme preference; pressing it stores an
explicit Light or Dark choice. Each browser stores its own preference;
appearance is not account data and does not synchronize between devices. The
toggle exposes its pressed state and does not flash the wrong appearance before
hydration.

#### Accessibility, language and target devices

Secondary text meets 4.5:1 contrast on its surface in both themes. Every
form field has a stable accessible name, even when it is presented with a
placeholder; placeholders never replace validation, field errors or essential
instructions. Every icon-only control has an accessible name and, where pointer
input is expected, a tooltip. Keyboard focus remains visible. Reduced-motion
preferences suppress spatial and decorative motion while keeping state
feedback. Registration shows the password requirement only after validation
rejects the submitted value.

The staff panel is designed primarily for tablet use, especially 11- to
14-inch touch-and-pointer devices, while remaining complete and responsive on
desktop and mobile; mobile staff use is expected to be infrequent. Staff roles
use the same interface; capabilities determine which operations are available.
Product copy in both frontends is Polish, and layouts must tolerate long labels.

#### Frontend state and API ownership

HeroUI is the component library for both applications. Zod validates frontend-owned form input and server-sent event payloads. Native `fetch` is the HTTP transport inside small handwritten REST request modules. SWR manages REST-backed client state in both frontends where caching, request deduplication, mutations, focus revalidation, or reconnect revalidation applies. React effects are reserved for synchronization with external systems rather than routine REST request orchestration.

The Spring API retains domain authority. Frontend controls and redirects provide user experience, while the backend enforces authorization.

#### Search indexing and link previews

Only public service information is indexable: the panel landing page, terms and privacy policy, and the customer home and privacy notice. Each has a canonical URL, Polish Open Graph metadata and a build-time preview image, and appears in its application's sitemap. Order pages, order manifests, scanning, the offline fallback, login, registration and the dashboard carry `noindex, nofollow`; order pages and manifests also send `X-Robots-Tag`. `robots.txt` does not block private paths, so crawlers can read their noindex rules. Order-page titles and previews are generic and never include the tracking reference, order label or status. Absolute URLs come from the build-time `NEXT_PUBLIC_CUSTOMER_APP_URL` and `NEXT_PUBLIC_PANEL_APP_URL` origins. Structured data and ranking work are out of scope.

### Backend

The backend uses Java 25 and Spring Boot 4. It is the only system of record and owns:

* order lifecycle and transition rules;
* staff authentication and authorization;
* tenant, location, and staff-access resolution;
* PostgreSQL persistence and migrations;
* Server-Sent Events publication;
* Redis-backed coordination and pub/sub;
* External Integration authentication, REST access, and webhooks;
* transactional order-event outbox processing;
* anonymous customer notification enrollment and durable Web Push delivery.

Browser-facing API requests go directly to the Spring API origin, through the
NGINX gateway in production and to `http://localhost:8080` locally. Both use
credentialed CORS scoped by frontend origin and browser resource family. Next.js route handlers are
limited to frontend rendering concerns.

### API contracts

REST is the integration boundary for frontends and third-party systems. During the first walking vertical slice, the endpoints are implemented as ordinary Spring MVC controllers and each frontend uses small handwritten TypeScript response types with native `fetch`, wrapped by SWR in Client Components that need server-state synchronization. The slice does not expose an OpenAPI document or generate REST clients. Next.js Server Actions and proxy route handlers do not replace or wrap the Spring REST boundary.

External Integration REST and webhook contracts remain language-agnostic and
use versioned resource families. OpenAPI publication, rendered reference
documentation, generated SDKs, and automated public-contract checks remain the
next integration increment. Introducing them must not move business rules or
API ownership out of the Spring application.
