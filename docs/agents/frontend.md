# Frontend conventions

## Contents

- [Frontend conventions](#frontend-conventions)
- [Contents](#contents)
- [Project conventions first](#project-conventions-first)
- [Components and effects](#components-and-effects)
- [Authenticated client state](#authenticated-client-state)
- [Tests](#tests)

## Project conventions first

Project requirements and conventions take precedence over generic advice:
retain HeroUI, Phosphor Icons, shared tokens and existing state/API patterns. Apply only
task-relevant rules; do not add dependencies, abstractions or animations solely
because generic advice suggests them. Before using view transitions, verify support in
the installed React/Next.js versions and configuration, preserve reduced-motion
handling and provide a usable fallback.

## Components and effects

Preserve the [visual design rules](../requirements/overview-technology.md#appearance-and-popup-behavior),
including typography, status presentation and icon restraint in the following sections.

- Use HeroUI (`@heroui/react`) for UI components. Charts are the exception:
  use shadcn chart components on Recharts, themed with shared semantic tokens.
  Each app's `styles/globals.css` imports only the `@heroui/styles` component
  sheets it renders: when a page starts using another HeroUI component, add its
  sheet and those of the components it composes (its `@heroui/react` source
  imports them from `../<name>/`), in the upstream `components/index.css` order.
- Use Phosphor Icons (`@phosphor-icons/react`, `*Icon` exports) for interface
  icons: regular weight by default and fill for "on" states. Do not add
  handwritten inline SVG icon components.
- Colours come from Apple's HIG system colours, kept in two layers in each
  app's `styles/globals.css`, with an identical primitives block in both apps.
  The `--system-*` primitives are the only place hex values appear; they switch
  between light, dark and `prefers-contrast: more`. HeroUI's semantic tokens
  (`--accent`, `--surface`, …) reference only those primitives. Components use
  semantic tokens only; Tailwind's default palette is disabled. Text uses the
  OS system font stack; do not add web fonts.
- Reserve React effects for synchronization with external systems such as
  EventSource or browser APIs; do not use effects for routine REST request
  orchestration.
- Keep the official `eslint-plugin-react-hooks` recommended rules enabled.

## Authenticated client state

- Route panel REST calls through the shared authenticated native-`fetch`
  client, scope staff SWR keys by account ID, and clear staff-owned state when
  the authenticated account changes.

## Tests

- Both apps test with Vitest. Panel tests render the real components with
  Testing Library in jsdom; replace only the network (`fetch` through
  `tests/helpers.tsx`, `EventSource`), browser APIs jsdom lacks (stubbed in
  `tests/setup.ts`) and framework hooks that need a running Next.js app, such
  as `next/navigation`. Do not mock HeroUI, React hooks or the panel's own
  modules.
