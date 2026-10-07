# Validation and test scope

- Run automated tests for the affected scope when practical. Browser or manual
  runtime checks remain opt-in and should run only when the user explicitly
  requests them or when they are needed to diagnose a runtime problem.
- Use repository-owned commands:
  - Customer frontend: `npm --prefix apps/customer-app run check`
  - Panel frontend: `npm --prefix apps/panel-app run check`
  - Backend: `apps/api/mvnw` from the repository root, or `./mvnw` from
    `apps/api`
  - Frontend dependencies: run `npm install` from the affected application and
    let it update `package-lock.json`
- Do not invoke installed frontend tools through `npx`, raw binaries, or
  `node_modules/.bin`. Use `npm run` or `npm --prefix`.
- Validate the affected scope when practical and always run
  `git diff --check`. Reserve production builds for dependency, build,
  Dockerfile, release-verification, or explicitly requested work.
- Report checks as passed, failed, blocked, or not run.

## Behavioral coverage

- Add or change tests only to protect an accepted product behavior, security
  boundary, data-integrity invariant, external contract, or concrete regression.
  State the failure the test would catch.
- Prefer the smallest behavioral test at the owning boundary. Extend existing
  coverage before adding a suite; do not repeat the same scenario across layers
  unless each layer verifies a distinct contract.
- Do not test trivial getters, pass-through wrappers, framework/library behavior,
  file existence, implementation structure, CSS classes, icon selection, or exact
  non-contract copy. Keep accessibility assertions about observable semantics
  and interaction.
- Assert outcomes independently of the implementation. Mock external boundaries,
  not the behavior being verified; call counts matter only when repetition itself
  violates the contract.
- No coverage-percentage targets or default test-per-function requirement.
  Presentation-only changes normally need lint/type-checking, not new tests.
- Keep focused coverage for authorization, tenant isolation, validation,
  consequential state transitions, atomicity, idempotency, and relevant races.
