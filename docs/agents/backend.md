# Backend conventions

- Organize code first by business feature and then by `api`, `application`,
  `domain`, and `infrastructure`. Add cohesive subpackages only where they
  represent a real conceptual boundary.
- Configure the application-wide `/api` base path with
  `server.servlet.context-path`; controller mappings declare resource-relative
  paths and do not repeat `/api`.
- Map projections through a static `from(...)` factory on API response records.
- Use Lombok `@RequiredArgsConstructor` for routine constructor injection,
  `@NonNull` for internal runtime null contracts, and `@Slf4j` for logging. Use
  Jakarta Bean Validation for API input.
- Prefer Spring Data derived query methods when the property path expresses the
  query; reserve manual `@Query` declarations for non-derivable or bulk
  operations.
- Use `var` when an initializer makes the local type evident. Indent Java with
  four spaces and never use tabs.
