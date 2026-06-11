# Aegis — Development Log

Append-only journal. Newest entries at the top. One entry per working session (or meaningful change).
Format: date · what changed · why · anything the next session needs to know.

---

## 2026-07-06 — Phase 3 (fine-grained authorization with OPA)
**What:**
- Chose **OPA (Rego)** as the Policy Decision Point. Added an `opa` service to docker-compose
  (:8181) that loads the versioned policies from `policies/`.
- **Policy-as-code:** `policies/authz.rego` (fail-closed `allow`) implements RBAC (admin role,
  `demo.read`/`demo.write` scopes) and ABAC (resource ownership on `/api/users/{id}`, time-of-day
  window for writes). `policies/authz_test.rego` covers all rules — run with `opa test policies/`.
- **Gateway PEP** (`com.aegis.gateway.authz`): `PolicyEnforcementFilter` (a reactive `GlobalFilter`,
  order 0) pulls the authenticated `JwtAuthenticationToken` from the Reactor security context,
  builds an `AuthorizationInput` (subject/action/resource/context) and asks the PDP. `PolicyDecision
  Point` is an interface; `OpaPolicyDecisionPoint` calls OPA over WebClient and **fails closed**
  (deny) on any error/timeout. Config under `aegis.authz.opa.*`.
- **Auth server:** replaced `KeyIdJwtCustomizer` with `AegisJwtCustomizer`, which keeps stamping the
  signing `kid` *and* now adds a `roles` claim to access tokens (from the principal's `ROLE_`
  authorities) so the gateway's RBAC rules have roles to work with.

**Why:** This is the differentiator — moving from authentication to real, externalized, testable
authorization. Policies live in-repo as code with their own tests; the enforcement point is at the
edge and fails closed, consistent with zero-trust.

**Gotchas / decisions:**
- **Security bug caught by a test:** the first filter used `switchIfEmpty(chain.filter(...))`, but
  `setComplete()` returns an empty `Mono<Void>`, so a *deny* completed empty and then forwarded the
  request anyway. Fixed by reducing to a Boolean decision first (`defaultIfEmpty(true)` for the
  no-JWT case) and branching on that. `PolicyEnforcementFilterTest` asserts deny → 403, no forward.
- The gateway starter does not auto-configure a `WebClient.Builder` under Boot 4.1 — build the
  client with `WebClient.create(url)` instead.
- `OAuth2TokenType` lives in `...server.authorization`, not `...oauth2.core`.

**Could not run here:** `opa test` (no OPA binary) and live gateway→OPA integration (Docker down).
Both are wired and will run in CI / with Docker up. Filter logic is covered by unit tests.

**Next session should:** Phase 4 (mTLS gateway↔services, OpenTelemetry tracing, structured logging,
per-client rate limits, secrets in Vault/KMS).

---

## 2026-07-05 — Phase 1 finished + Phase 2 (real identity & persistence)
**What:**
- **Finished Phase 1:** generated the Maven wrapper; added one integration test per module
  (`AuthServerIntegrationTest` on Testcontainers Postgres; `GatewaySecurityTest` via WebTestClient;
  `WhoAmIControllerTest` on RANDOM_PORT with a real RS256-signed JWT) plus a TOTP unit test.
- **Phase 2 — auth server, nothing critical in memory anymore:**
  - JPA `RegisteredClientRepository` (`client/`), seeded `aegis-web-client` + confidential
    `aegis-service-client` (client_credentials) via an idempotent `DataInitializer`.
  - JPA `UserDetailsService` (`user/`) over `app_user` / `app_user_role`; `AegisUserDetails`
    surfaces lockout via `isAccountNonLocked()`.
  - Persistent, **rotatable** RSA signing keys (`jwk/`): `signing_key` table, `RotatingJwkSource`
    publishes all active keys, `KeyIdJwtCustomizer` stamps the current `kid` so Nimbus can pick one
    signer during a rotation overlap. `POST /api/admin/keys/rotate` (ADMIN).
  - Account lifecycle (`account/`, `web/RegistrationController`): registration, password policy,
    lockout after N failures driven by Spring Security auth events.
  - MFA TOTP (`mfa/`): RFC 6238 implemented directly (verified against the spec vector), wired into
    form login via `MfaAuthenticationProvider` + a custom Thymeleaf login page with an `otp` field.
  - Append-only audit log (`audit/`): `auth_audit_event`, written in its own REQUIRES_NEW tx.
  - Flyway V2–V5 add the lockout/MFA columns, `signing_key`, `oauth2_registered_client`, and
    `auth_audit_event`. `ddl-auto: validate` keeps entities honest against the migrations.

**Why:** Deliver the Phase-2 goal — persistent identity, rotatable keys, MFA, lockout, and an audit
trail — on a security-first foundation, each shortcut from Phase 1 now replaced.

**Gotchas hit (Boot 4.1 / Spring Security 7 are new):**
- `OAuth2AuthorizationServerConfiguration.applyDefaultSecurity()` was **removed** — use
  `new OAuth2AuthorizationServerConfigurer()` + `http.securityMatcher(...).with(...)`.
- Boot 4.1 ships **Jackson 3** (`tools.jackson`) for HTTP, but Spring Security's persistence
  serialization is still Jackson 2 and **optional** — added `com.fasterxml.jackson.core:jackson-databind`
  explicitly.
- Boot 4.1 references `${testcontainers.version}` with **no default** — pinned it in the parent POM.
- Boot 4 reorganized test modules: `TestRestTemplate` / `@AutoConfigureMockMvc` weren't resolvable,
  so tests use RANDOM_PORT + `RestClient` / `WebTestClient` built from `@LocalServerPort`.

**Env note:** built/verified with JDK 24 via `-Dmaven.compiler.release=24` (JDK 25 not installed on
this box); code uses no Java-25-only syntax.

**Next session should:** start **Phase 3** (policy engine / PDP). Also worth adding: CI (Phase 5),
and moving signing-key private material out of the DB into a KMS (Phase 4).

---

## 2026-07-04 — Phase 1 scaffold created
**What:**
- Chose the project: **Aegis**, a Zero-Trust IAM + policy-enforcing gateway (selected from a
  shortlist of security-product ideas as the best fit for a Java dev targeting top companies).
- Confirmed latest versions (July 2026): Java 25 LTS, Spring Boot 4.1.0, Spring Cloud 2025.1.2.
- Created multi-module Maven project with three modules:
  - `aegis-auth-server` (9000): OAuth2/OIDC authorization server. In-memory client
    (`aegis-web-client`, Auth Code + PKCE, 5-min access token, rotating refresh), in-memory user
    (`admin`/`changeit`), runtime-generated RSA JWK. Flyway V1 migration adds `app_user` tables
    (not yet wired to logic).
  - `aegis-gateway` (8080): reactive gateway using the renamed
    `spring-cloud-starter-gateway-server-webflux`. Validates JWTs as an OAuth2 resource server;
    one route `/api/demo/**` → resource-demo with a Redis token-bucket rate limiter (10 rps, burst 20).
  - `aegis-resource-demo` (8081): servlet resource server; `GET /api/demo/whoami` echoes JWT claims.
- Added `docker-compose.yml` (postgres:18, redis:8), `.gitignore`, and full docs set
  (CLAUDE.md, README, ARCHITECTURE, ROADMAP, THREAT_MODEL, this log).

**Why:** Establish a clean, correct, security-first foundation that boots end-to-end before adding
depth. Every in-memory / dev-only shortcut is intentional and tracked in ROADMAP Phase 2.

**Next session should:**
1. Verify the build compiles against Boot 4.1 / Spring Security 7 (these are new; some APIs differ
   from Boot 3.x — check reference docs if a symbol doesn't resolve).
2. Finish Phase 1 checklist in ROADMAP: add Maven wrapper, write the end-to-end smoke test
   (get token → call gateway → 200 from whoami), add one integration test per module.
3. Then start Phase 2 (JPA-backed clients/users + persistent, rotatable signing key).

**Notes / risks:**
- Boot 4.x is very new; if `mvn` reports unresolved artifacts, double-check the exact starter
  coordinates and that Maven Central has the 4.1.0 / 2025.1.2 artifacts.
- Nothing here is production-ready yet — it is a scaffold. Do not deploy.
