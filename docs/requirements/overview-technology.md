# Purpose, technology and ownership

## Contents

- [Purpose, technology and ownership](#purpose-technology-and-ownership)
- [Contents](#contents)
- [1. Purpose and Scope](#1-purpose-and-scope)
- [2. Technology and Ownership Decisions](#2-technology-and-ownership-decisions)
- [2.1 Frontend applications](#21-frontend-applications)
- [Appearance and popup behavior](#appearance-and-popup-behavior)
- [Controls, typography and accessibility](#controls-typography-and-accessibility)
- [Navigation, cards and interaction](#navigation-cards-and-interaction)
- [Frontend state and API ownership](#frontend-state-and-api-ownership)
- [2.2 Backend](#22-backend)
- [2.3 API contracts](#23-api-contracts)

## 1. Purpose and Scope

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

## 2. Technology and Ownership Decisions

### 2.1 Frontend applications

Both frontends use Next.js 16, React 19, TypeScript, Tailwind CSS 4, HeroUI 3,
and Lucide React. They remain separate because they serve different audiences
and have different authentication, PWA, caching, and release concerns. The
customer application uses a custom TypeScript service worker built with Serwist
in configurator mode and owns its offline snapshots in IndexedDB.

Both frontends share one coherent, Apple-inspired web design language while
retaining browser-native behavior. They use the same semantic visual system,
interaction principles, and quality standard; differences in information
density reflect the customer and staff tasks rather than different brand or
role-specific styling. The **Kairos** name is the only visual-brand artifact
that must be preserved during the redesign. Icons, colors, typography,
materials, and other visual assets may be replaced.

The visible standalone **Kairos** wordmark uses one shared monospace brand
role in both frontends while interface and body text remain in the system
font. On the anonymous staff sign-in and onboarding surface, the wordmark is
centered and visually dominant without a descriptor or explanatory sign-in
sentence beneath it.

Staff sign-in, public registration, and invitation registration use one shared
form-page layout with the same responsive width, wordmark, heading, and field
spacing. The layout centers its content vertically in the dynamic viewport using
available space rather than a fixed top offset. It reserves a common form area
based on the registration control heights and spacing tokens, so switching
between sign-in and registration keeps the wordmark, heading, and first field
stable. Short viewports allow the content to grow and scroll without clipping;
anonymous pages do not reserve space for the signed-in bottom navigation. Primary submit buttons span the field width and use text without a
trailing arrow. Account-navigation sentences beneath the forms are centered.
The shared form-page layout owns form spacing, field and button widths, and
footer presentation rather than repeating those rules in each page. Navigation
sentences follow the form action with the same standard spacing; any reserved
space for stable page alignment follows the footer instead of separating it
from the button. This also
covers invitation registration and signed-in registration recovery actions.
Focused management dialogs retain their shared HeroUI body/footer anatomy with
adjacent primary and secondary actions, using the same spacing and control-size
tokens rather than the standalone authentication-page layout.

#### Appearance and popup behavior

Keep popups quick to read: use a clear title and direct actions, and omit
secondary text that repeats the title or action. Routine confirmations such as
logout omit explanatory captions; titles and buttons communicate the decision.
Retain concise instructions only for non-obvious setup, irreversible effects,
access/account cascades, recovery and one-time secrets. Confirmation headers
place one compact warning icon alongside the title; they never repeat the
button's action icon. Warning icons use yellow/amber in both appearances.

The shared visual character is direct, neutral, and hospitality-oriented
without restaurant-themed decoration. Light appearance uses a true white page,
light-gray secondary regions, and one restrained light-blue selection/accent;
dark appearance uses black, dark gray, and the equivalent blue. Semantic error
or destructive color is reserved for the states that require it. Hierarchy
comes primarily from typography, alignment, spacing, and separators rather
than visible containers. Routine content must not be wrapped in cards, bordered
boxes, or decorative materials. Elevation is reserved for genuinely floating
layers such as menus and modal sheets.
Both frontends use one backdrop treatment for every modal, confirmation dialog,
and drawer: a neutral tint at 20% opacity derived from the foreground color and
an 8-pixel background blur. It follows Light and Dark appearance, keeps the
underlying page visible, and avoids a solid black backdrop. Opacity applies to
the backdrop color, never the dialog or its contents. The rule lives in each
application's shared stylesheet rather than per-popup overrides. Modal,
confirmation, and drawer headers have one 16-pixel gap before their content,
whether the body follows directly or is wrapped in a form; shared stylesheet
rules cover both structures so form submission wrappers cannot remove spacing.
Every staff modal and confirmation uses one shared HeroUI popup shell that owns
its backdrop, centered placement, top-right Close control, dismissal policy, and
header clearance. Staff popup backdrops animate their tint and blur over 150 ms
on entry and 100 ms on exit using ease-out timing, without applying backdrop opacity to the popup
contents. Reduced-motion preferences disable this backdrop animation.
Optional popups close through Close, an outside click, or
Escape, including while a request is pending; closing does not cancel an
already submitted operation or permit duplicate submission. Queue location
selection is temporarily unavailable while an order mutation is pending, so
completion stays associated with the submitted queue. Required steps such
as first-location onboarding explicitly use non-dismissible required mode,
which hides Close and rejects outside-click, Escape, and close-slot dismissal.
Dropdowns and selectors retain their native menu behavior rather than using the
popup shell. HeroUI retains focus management and focus restoration.
Dismissing invitation creation while its request is pending does not discard
its once-revealed result: success presents the link, and closing and reopening
New account retains it until the user explicitly confirms saving it.

#### Controls, typography and accessibility

Both frontends use one CSS-defined foundation before component styling: a
4-point spacing basis with 4, 8, 12, 16, 24, 32, and 48-pixel steps; 32-pixel
compact, 40-pixel regular, and 44-pixel touch control sizes; small 6-pixel,
medium 10-pixel, and large 14-pixel corner radii; a restrained system-font type
scale; and one-pixel separators. Pill geometry is limited to controls whose
meaning requires it. Staff controls retain comfortable touch targets while
queue content remains compact enough for an operational tablet workspace.
Text inputs and selectors use the regular control height by default. When an
input and actions share an inline control row, that row owns one semantic height
for every peer so their alignment can be changed centrally without per-control
overrides.

Routine editable text fields and single-choice selectors use compact,
placeholder-led presentation instead of visible labels beside or above the
control. Reusable HeroUI form controls retain associated visually hidden labels
so each field has a stable accessible name after a value is entered. Placeholder
copy is concise, sentence case, and task-specific: text fields name the expected
value (for example, **Email** or **Location name**) and empty selectors use
**Select location**. Optional fields identify their default when needed, such as
**Order label (automatic if empty)**. Placeholder typography and color come
from shared semantic styles rather than per-page overrides.
In dark appearance, form fields use a lighter dark-gray surface than the page
and modal backgrounds so their boundaries remain visible without focus.

Visible labels remain for native date/time inputs, read-only values, and grouped
choices that cannot communicate their purpose through a placeholder. Validation,
field-specific password errors, and consequential confirmation instructions remain
available outside the field; placeholders do not replace essential instructions.
Registration omits persistent password-guidance copy and shows the corrective
requirement beside Password only after validation rejects the submitted value.

Both frontends provide Light, Dark, and System appearance preferences, with
System as the default. Each browser stores its own preference; appearance is
not account data and does not synchronize between devices. The appearance
trigger keeps the same rounded icon-control geometry while its menu is open and
uses one valid interactive element rather than nested controls. The staff panel is
designed primarily for tablet use,
especially 11- to 14-inch touch-and-pointer devices, while remaining complete
and responsive on desktop and mobile. Mobile staff use is expected to be
infrequent. Staff roles use the same interface language and workspace styling;
capabilities still determine which operations are available. Product copy in both frontends is Polish. Layouts must tolerate long labels
without depending on short text.

#### Navigation, cards and interaction

Neither frontend uses a persistent global top bar. Customer pages
place only the utilities relevant to that page in a quiet trailing control
group; the tracking view aligns Notifications and Appearance on one level. On
tablet and desktop, staff workspace navigation is a flat, rounded segmented control centered independently of the trailing Appearance and Account
utilities on the same level. It uses only a solid secondary background and
selected segment, without glass effects, decorative borders, or shadows.

Interactive staff cards across Orders, Locations, Accounts, and Integrations
use one reusable Panel Card anatomy: a direct full-card target, primary identity,
supporting metadata, and an optional trailing accessory or action group.
Location, Account, Invitation and Integration cards omit navigation arrows;
the full-card target and motion communicate that they are clickable. They
share the medium corner scale, page surface at rest, one-pixel separators, and
an 8-pixel inter-card rhythm with immediate motion feedback on hover and press,
and a subtle neutral focus indicator for keyboard navigation only. Management collection cards use restrained colour surfaces for
enabled, disabled, and pending status; explicit status appears in their detail
popup. Order selection retains its blue selection surface without a decorative
leading edge. Cards keep identity and compact accessories inline across phone, tablet,
and desktop widths; essential text reflows instead of being removed.

Routine interactive surfaces in both frontends use one shared short motion
system: a restrained scale lift on precise-pointer hover, immediate compression
while pressed, and a subtle spring-like return on release. Hover does not change
background, text, border colour or opacity. Mouse and touch focus do not add
rings; keyboard focus remains visible with a subtle neutral outline. Stateful spatial
transitions that communicate a changed location or support direct manipulation
remain purposeful exceptions.
Reduced-motion preferences retain non-spatial state feedback while suppressing
the interaction scaling.

Transient notices in both frontends use the shared flat secondary surface and
medium corner scale. Their status indicator is centered against the complete
text block, and dismissible notices keep a trailing, icon-only Close action in
a stable column on desktop and mobile.

Action controls are icon-first where a familiar symbol communicates their
meaning, including Home, Scan, Notifications, Appearance, QR, More, Close, and
Switch camera. Major actions, including creation, editing, saving, disabling, enabling and
deletion, pair a Lucide icon with visible Polish text. Secondary popup and prompt
actions, including Cancel, Not now and Close, use explicit Polish text such as
**Anuluj**, **Nie teraz** and **Zamknij**. Top-right Close, appearance and
account-switching utilities remain compact icon-only controls. Entity name editing
uses an icon-only button immediately beside the name, with a transparent
background and no border. Paired popup
actions each occupy half of the available row width and wrap long text. Every icon-only
control has an accessible name and, where pointer input is expected, a tooltip.
Both frontends source interface glyphs from Lucide React so repeated actions
share one stroke, proportion, and optical language. Application code does not
define handwritten inline SVG icon components.

#### Frontend state and API ownership

HeroUI is the component library for both applications. Zod validates frontend-owned form input and server-sent event payloads. Native `fetch` is the HTTP transport inside small handwritten REST request modules. SWR manages REST-backed client state in both frontends where caching, request deduplication, mutations, focus revalidation, or reconnect revalidation applies. React effects are reserved for synchronization with external systems rather than routine REST request orchestration.

The Spring API retains domain authority. Frontend controls and redirects provide user experience, while the backend enforces authorization.

### 2.2 Backend

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

Browser-facing API requests go directly to the Spring API origin through the
shared NGINX gateway. The local HTTPS environment uses credentialed CORS scoped
by frontend origin and browser resource family. Next.js route handlers are
limited to frontend rendering concerns.

### 2.3 API contracts

REST is the integration boundary for frontends and third-party systems. During the first walking vertical slice, the endpoints are implemented as ordinary Spring MVC controllers and each frontend uses small handwritten TypeScript response types with native `fetch`, wrapped by SWR in Client Components that need server-state synchronization. The slice does not expose an OpenAPI document or generate REST clients. Next.js Server Actions and proxy route handlers do not replace or wrap the Spring REST boundary.

External Integration REST and webhook contracts remain language-agnostic and
use versioned resource families. OpenAPI publication, rendered reference
documentation, generated SDKs, and automated public-contract checks remain the
next integration increment. Introducing them must not move business rules or
API ownership out of the Spring application.
