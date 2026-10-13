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
retain HeroUI, Lucide, shared tokens and existing state/API patterns. Apply only
task-relevant rules; do not add dependencies, abstractions or animations solely
because a skill suggests them. Before using view transitions, verify support in
the installed React/Next.js versions and configuration, preserve reduced-motion
handling and provide a usable fallback.

## Components and effects

- Always use HeroUI (`@heroui/react`) for UI components.
- Use Lucide React for interface icons; do not add handwritten inline SVG icon
  components.
- Reserve React effects for synchronization with external systems such as
  EventSource or browser APIs; do not use effects for routine REST request
  orchestration.
- Keep the official `eslint-plugin-react-hooks` recommended rules enabled.

## Authenticated client state

- Route panel REST calls through the shared authenticated native-`fetch`
  client, scope staff SWR keys by account ID, and clear staff-owned state when
  the authenticated account changes.
