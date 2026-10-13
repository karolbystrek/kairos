# Frontend conventions

## Contents

- [Frontend conventions](#frontend-conventions)
- [Contents](#contents)
- [Using skills](#using-skills)
- [Components and effects](#components-and-effects)
- [Authenticated client state](#authenticated-client-state)

## Using skills

Use installed skills when useful, respecting their invocation scope.
Project requirements and conventions take precedence over generic skill advice:
retain HeroUI, Phosphor Icons, shared tokens and existing state/API patterns. Apply only
task-relevant rules; do not add dependencies, abstractions or animations solely
because a skill suggests them. Before using view transitions, verify support in
the installed React/Next.js versions and configuration, preserve reduced-motion
handling and provide a usable fallback.

## Components and effects

- Use HeroUI (`@heroui/react`) for UI components. Charts are the exception:
  use shadcn chart components on Recharts, themed with shared semantic tokens.
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
