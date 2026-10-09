# Backend conventions

## Contents

- [Backend conventions](#backend-conventions)
- [Contents](#contents)
- [Feature structure and API mapping](#feature-structure-and-api-mapping)
- [Java and persistence conventions](#java-and-persistence-conventions)

## Feature structure and API mapping

- Organize code first by business feature and then by `api`, `application`,
  `domain`, and `infrastructure`. Add cohesive subpackages only where they
  represent a real conceptual boundary.
- Configure the application-wide `/api` base path with
  `server.servlet.context-path`; controller mappings declare resource-relative
  paths and do not repeat `/api`.
## Java and persistence conventions

- Map projections through a static `from(...)` factory on API response records.
- Use Lombok `@RequiredArgsConstructor` for routine constructor injection,
  `@NonNull` for internal runtime null contracts, and `@Slf4j` for logging. Use
  Jakarta Bean Validation for API input.
- Prefer Spring Data derived query methods when the property path expresses the
  query; reserve manual `@Query` declarations for non-derivable or bulk
  operations.
- Use `var` when an initializer makes the local type evident. Indent Java with
  four spaces and never use tabs.
- Import Java classes and use their simple names in production and test code,
  including annotations and generic types. Avoid fully qualified class names
  in code; qualify only when a name collision makes it necessary. Use explicit
  imports rather than wildcard imports.
- Prefer straightforward control flow and existing feature patterns. Extract
  helpers for a repeated operation or a meaningful responsibility, not merely
  to wrap a single call; avoid speculative interfaces and abstractions.
