# Frontend conventions

- Always use HeroUI (`@heroui/react`) for UI components.
- Use Lucide React for interface icons; do not add handwritten inline SVG icon
  components.
- Reserve React effects for synchronization with external systems such as
  EventSource or browser APIs; do not use effects for routine REST request
  orchestration.
- Keep the official `eslint-plugin-react-hooks` recommended rules enabled.
- Route panel REST calls through the shared authenticated native-`fetch`
  client, scope staff SWR keys by account ID, and clear staff-owned state when
  the authenticated account changes.
