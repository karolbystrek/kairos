# Frontend conventions

## Contents

- [Frontend conventions](#frontend-conventions)
- [Contents](#contents)
- [Frontend skill selection](#frontend-skill-selection)
- [Components and effects](#components-and-effects)
- [Authenticated client state](#authenticated-client-state)

## Frontend skill selection

Read the selected skill's `.agents/skills/<name>/SKILL.md` before applying it.
For frontend design and visual reviews, use `emil-design-eng` and `apple-design`
together as the baseline. Select additional skills to fit the task; this is a
guide, not a checklist requiring every skill on every frontend change.

| Task | Skill |
| --- | --- |
| Build or change animations | `animate` |
| Review motion / audit motion across an app | `review-animations` / `improve-animations` |
| Mobile, touch and PWA interaction | `mobile-native` |
| Stress-test layouts with extreme content | `break-ui` |
| Write, review or optimize React/Next.js code and data fetching | `vercel-react-best-practices` |
| Refactor component APIs, composition or shared state | `vercel-composition-patterns` |
| Route, shared-element or state view transitions | `vercel-react-view-transitions`, alongside `animate` |
| Review UI accessibility, usability and web conventions | `web-design-guidelines` |

Use other installed skills when useful, respecting their invocation scope.
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
