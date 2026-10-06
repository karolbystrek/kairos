---
status: superseded
date: 2026-10-05
---

# Replace Google Identity Platform with Clerk

Clerk replaces Google Identity Platform as the managed authentication provider.
Enable email/password and Google social sign-in. The existing partial Google
implementation is not complete or deployment-ready and must be adapted.

All provider-independent decisions in [ADR 0001](0001-google-identity-platform-authentication.md)
remain accepted: remove username, preserve Account UUIDs, retain verified public
tenant onboarding and fixed manually shared member invitations, keep tenant and
location authorization in Kairos, use JWT-based authentication, preserve local
and global logout semantics, simplify tab coordination, and integrate through
Spring while keeping credentials unavailable to Kairos frontend JavaScript.
Google-specific token formats, refresh mechanisms, credential configuration,
email APIs, cutoff claims, and expiry assumptions do not carry over unchanged.

Clerk supplies an official Java Backend SDK and public JWT verification keys;
Spring Boot is a supported integration target through Java APIs and standard
Spring Security JWT/OAuth2/OIDC facilities. TypeScript is not required for the
backend. Clerk supports email/password and Google social connections.

The recommended integration to preserve the accepted backend boundary is a
Clerk OAuth application using OIDC authorization-code flow with PKCE: Spring
initiates a redirect to Clerk-hosted authentication, receives the callback,
exchanges the code on the backend, and handles Kairos HttpOnly credentials.
This recommendation still needs detailed Clerk configuration and lifecycle
verification before implementation. Standard Clerk frontend SDK integration
exposes its short-lived session JWT to application JavaScript and must not be
substituted silently for the accepted credential boundary.

References:
* [Official Java SDK](https://github.com/clerk/clerk-sdk-java)
* [Clerk OIDC support](https://clerk.com/docs/guides/configure/auth-strategies/oauth/single-sign-on)
* [Clerk frontend token architecture](https://clerk.com/docs/guides/how-clerk-works/overview)
* [Google social connections](https://clerk.com/docs/guides/configure/auth-strategies/social-connections/overview)
