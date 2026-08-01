# Aegis — Development Log

Append-only journal. Newest entries at the top. One entry per working session (or meaningful change).
Format: date · what changed · why · anything the next session needs to know.

---

## 2026-08-29 — Interactive demo console + the platform becomes deployable
**What:** Phase 6's two open items — something worth showing, and somewhere to show it.

**The console (`aegis-gateway`, `static/index.html`, served at `/`).** A framework-free, CDN-free
static page that drives a real Authorization Code + PKCE flow against the auth server and then
fires real requests through the gateway. Seven scenarios: allowed read, time-windowed write, own
profile, someone else's profile, no token, tampered signature, 25-request burst. Each shows status,
latency, the policy decision header, and the response body, and states whether that matched the
expectation. The decoded-token panel counts the 5-minute expiry down live.
- `/` is mapped by an explicit `RouterFunction` rather than the static welcome-page handler,
  because `/` is *also* the registered OAuth2 redirect URI: anything that answers it with a
  redirect to `/index.html` drops the `?code=` query and strands the flow.
- Config (`issuerUri`, `clientId`, scopes, sandbox creds) comes from `GET /playground/config`, so
  one image runs unchanged in every environment.
- The auth server now allows CORS on `/oauth2/**` for the console's origin — the code-for-token
  exchange is a `fetch()` from a different origin, and without it the browser discards the
  response before the page sees it. Credentials are deliberately not allowed (PKCE needs no cookie).

**Two new endpoints** so the policy demo has real resources: `POST /api/demo/echo` (governed by the
time-of-day rule) and `GET /api/users/{id}` (governed by the ownership rule), plus a `user-profile`
route on the gateway. The Rego policy already covered both cases — **no policy change was needed**,
which is the nice part: the rules were written against a resource model, not against endpoints.

**The PEP now explains its refusals.** `PolicyEnforcementFilter` stamps
`X-Aegis-Policy-Decision: allow|deny`, and a denial returns JSON naming the evaluated subject,
roles, scopes, action, path and UTC hour. The header is registered via `beforeCommit` — set eagerly
it does not survive proxying, because the routing filter copies the downstream headers over it.
The body echoes only claims the caller already holds; never the token, never the ruleset.

**Deployability.** Every URL, port and credential is env-driven now. New `cloud` profile on all
three services turns OTLP export off (no collector out there; otherwise every span retries a dead
endpoint) and sets `server.forward-headers-strategy: framework` — without that, an auth server
behind a TLS-terminating proxy builds `http://internal-host:port` redirects and the OAuth2 flow
breaks in a way that looks like a certificate problem. Shipped `deploy/compose.prod.yml` +
`deploy/Caddyfile` (single VM, automatic Let's Encrypt) and `render.yaml`; `policies/Dockerfile`
bakes the Rego into an immutable image so deployed authorization is as reviewable as the code.
Guide in `docs/DEPLOYMENT.md`.

**Bug found and fixed — this one would have sunk the live demo.** The gateway's
`cloud.gateway.server.webflux.routes` block was indented under `aegis:` instead of `spring:`.
Unknown YAML keys bind to nothing and nothing warns, so the gateway started perfectly with an
**empty route table** and every proxied path returned 404. It had presumably been that way since
the `aegis:` block was inserted between `spring:` and the routes. Now covered by
`GatewayRouteConfigurationTest`, which asserts both routes bind, point at the resource service, and
carry the rate limiter and circuit breaker. *Startup succeeding is not evidence that a gateway can
route.*

**`DataInitializer` seeding is now an upsert for the web client.** Redirect URIs come from config,
and a deployment that moves to a new public URL must have the new URI registered — skipping the
update whenever a row already existed would make the first deploy work and every later one fail.
It reuses the existing primary key so `save()` updates in place. Accounts stay create-once (a
password changed through the UI is never silently reset). Also seeds a **non-admin `alice`** for
the sandbox: an admin is allowed everything by the first RBAC rule, so an admin session would sail
through every scenario and never show a denial.

**Verified live** against Postgres 18 + Redis 8 + the new OPA image, all three services running:
- gateway routing restored (200 with `X-RateLimit-*` headers, proving the route proxies);
- `X-Aegis-Policy-Decision: allow` on the happy path, `deny` + explanatory JSON on refusal;
- ownership denial, missing-token 401, tampered-signature 401 all as expected;
- the burst scenario: 25 sent → **20 allowed, 5 × 429**, exactly the configured burst capacity;
- 9/9 Rego tests pass *inside* the built policy image; PDP allows alice's write at 13:00 UTC and
  denies the identical request at 22:00;
- auth server **restarted** against the existing database: the client round-trips through the
  Duration deserializer and is re-registered in place — two boots, still exactly two client rows.

**Not verified by me:** the interactive password step of the login form (I do not type credentials
into forms). The token exchange was exercised with a token minted through the API instead. Worth
30 seconds of manual confirmation before recording.

**Test suite:** 18 gateway tests, 13 resource-demo, 44 auth-server non-Docker — all green. The two
Testcontainers ITs still fail on this machine (Testcontainers cannot reach the Docker Desktop named
pipe even though the CLI can); unchanged from before, and they run on CI.

**Next session:** run `docs/DEPLOYMENT.md` path A, put the real URL in the README's Live demo
section (currently `#`), change the seeded `admin`/`changeit` password before linking publicly,
then record `docs/DEMO.md` Script A.

## 2026-08-16 — First live run of the auth server: two startup blockers fixed
**What:** Ran the auth server against a real Postgres 18 for the first time outside Testcontainers
and walked all five UI pages in a browser. Startup failed twice; both were real defects, not
environment noise:
- **Flyway never ran.** Boot 4.x split `spring-boot-autoconfigure` into per-technology modules, so
  `flyway-core` on its own is just the library — `spring.flyway.*` stays inert without
  **`org.springframework.boot:spring-boot-flyway`**, which carries the auto-configuration and is
  not a transitive dependency of flyway-core. Symptom: no Flyway log lines at all, then Hibernate's
  `ddl-auto: validate` aborts with `Schema validation: missing table [app_user]`. Added the module
  to `aegis-auth-server/pom.xml`; migrations V1–V6 now apply (7 tables created).
- **Client seeding violated a NOT NULL constraint.** `JpaRegisteredClientRepository.toEntity()`
  copied `RegisteredClient.getClientIdIssuedAt()` straight onto the entity. That property is
  optional on `RegisteredClient` and `DataInitializer` never sets it, so the copy overwrote the
  entity's `Instant.now()` field default with null and the insert failed against
  `client_id_issued_at TIMESTAMPTZ NOT NULL`. Now coalesces to `Instant.now()` in the repository,
  so it holds for any client saved through it, not just the seeded pair.

**Verified live:** `/login`, `/register`, `/account`, `/account/mfa` (QR renders as inline SVG for a
real pending enrollment), `/admin/audit`. Signed in as `admin`/`changeit`; the audit console showed
the `LOGIN_SUCCESS` row, and "Verify chain integrity" reported **chain intact** and then appended its
own `AUDIT_CHAIN_VERIFIED` row to the chain — the documented behaviour, observed end-to-end.

**Why:** These two bugs made a fresh-database boot impossible, so they blocked the Phase 6 live
deploy outright. Both were invisible locally because the Testcontainers ITs are skipped on this
machine (no headless Docker, per the build-env notes).

**Next session:**
1. **Check CI.** The Flyway gap should have failed `AuthServerIntegrationTest` and
   `RefreshTokenRotationIntegrationTest` on the GitHub runner — if those went green, the ITs are not
   exercising a fresh schema and that is worth understanding before trusting them.
2. Consider a root (`/`) mapping: after login Spring Security redirects to `/`, which has no
   controller, so a successful sign-in currently lands on a Whitelabel 404. `/account` is the
   obvious landing page.
3. Local run notes: other projects on this machine hold 5432/6379/3000, so Postgres was run on
   **5435** (`--spring.datasource.url=jdbc:postgresql://localhost:5435/aegis_auth`); the JVM must be
   started with `-Duser.timezone=Asia/Kolkata` because Postgres 18 rejects the legacy `Asia/Calcutta`
   alias the Windows JVM reports. Both are captured in `.claude/launch.json` (`auth-server` config).

## 2026-07-14 — Technology stack reference guide (docs/TECH_STACK.md)
**What:** New `docs/TECH_STACK.md` — a study/interview-prep reference covering every technology in
the platform: what it is, where it's used in this repo, why it was chosen, its USP, and the
alternatives considered. Includes a one-page decision map (tech → module → one-line why) and a
"why not X?" cheat sheet (Keycloak, Nginx/Kong, sessions vs JWT, `@PreAuthorize` vs OPA, reactive
vs servlet). Contents verified against the actual module POMs, not just memory.

**Why:** The developer asked for a single document to build understanding of which technology is
used where and why — CLAUDE.md tracks *status*, ARCHITECTURE.md tracks *structure*; this fills the
*rationale* gap and doubles as interview prep.

**Next session:** keep TECH_STACK.md's decision map in sync when dependencies change (same rule as
CLAUDE.md's status section). No code changes this session.

## 2026-07-07 (night) — Account page + browser MFA enrollment with QR code
**What:** Completed the self-service story: register → sign in → enroll MFA from the browser.
- **`MfaEnrollmentService`** extracted from `MfaController` (same pattern as registration):
  the JSON API (`/api/mfa/**`) and the new pages share one path, so the two-step rule
  (secret stored *disabled*, MFA on only after a code verifies — no locking yourself out)
  and the `MFA_ENROLLED` audit apply identically. API responses unchanged.
- **`/account`** (`AccountPageController` + `account.html`): identity panel (username, role
  chips) + MFA status (ENABLED / PENDING / OFF chips) with enable / continue-setup /
  re-enroll actions.
- **`/account/mfa`** (`account-mfa.html`): scannable **QR code** of the `otpauth://` URI +
  manual secret fallback + activation form (inline error on a wrong code, flash success on
  activation). QR via **zxing:core** (encoding only, zero transitive deps — TOTP stays
  hand-rolled since it's the showcased mechanic; QR is just presentation) rendered by
  `QrSvgRenderer` as inline SVG (one path, module-unit viewBox, white backing for scanner
  contrast, no imaging stack).
- Topbar extracted to a parameterized fragment (`fragments/ui :: topbar(crumb)`) — admin
  and account pages share it. New CSS: `.panel`, `.kv`, `.qr-box`, `.chip-ok/.chip-warn`.
- **Tests (40/40 green, no Docker):** `MfaEnrollmentServiceTest` (5 — pending-not-active,
  valid/wrong code, activate-without-enroll, re-enroll resets), `QrSvgRendererTest` (3 —
  well-formed SVG, deterministic, quiet zone respected), `UiPagesRenderTest` +5 (account
  states, QR page, wrong-code error, auth required). Preview dumps extended
  (`account-mfa-off/on/setup.html`); QR verified in-browser: viewBox 49×49 (version 4 +
  quiet zone), 837 modules, aria-labeled.

**Why:** MFA existed but was unusable by humans (hand-copying a Base32 secret out of a JSON
response). A scan-and-confirm flow makes the security feature real — and demoable.

**Note:** error strings avoid apostrophes — Thymeleaf escapes `'` to `&#39;`, which broke a
content assertion ("didn't" → "didn&#39;t").

## 2026-07-07 (evening) — UI: registration page + admin audit-trail console
**What:** Turned the auth server's web UI from a single login page into a small product:
- **Design system extracted:** the login page's inline styles moved to `static/css/aegis.css`
  and the brand panel/logo into Thymeleaf fragments (`templates/fragments/ui.html`); login,
  registration, and admin pages now share one look (still zero JS / zero CDN).
- **`GET|POST /register`** (`RegistrationPageController` + `register.html`): browser-facing
  sibling of the JSON `POST /api/register` — same `RegistrationService`, so password policy,
  uniqueness, and auditing are identical. Confirm-password + inline errors; success redirects
  to `/login?registered`. Login page now links to it (dev hint no longer tells humans to curl).
- **`GET /admin/audit`** (`AuditTrailPageController` + `admin/audit.html`): admin console for
  the hash-chained audit trail — 50 newest events (colored type chips, truncated hashes,
  legacy rows marked "pre-chain") and a **Verify chain integrity** button (POST, because it
  appends `AUDIT_CHAIN_VERIFIED` to the chain it checks) with intact/broken banner; the first
  broken row is highlighted. Head hash shown for external anchoring.
- **Security config:** `/register` + `/css/**` allow-listed; `/admin/**` now requires
  ROLE_ADMIN alongside `/api/admin/**`.
- **Tests:** `UiPagesRenderTest` (@WebMvcTest, 10 tests) renders every page through the real
  Thymeleaf view layer — no DB/Docker — and pins access rules (anon → login redirect,
  non-admin → 403). `UiPreviewDumpTest` writes rendered pages to `target/ui-preview/` for
  visual review (serve statically via `.claude/launch.json` → `ui-preview`). Suite: 26/26 green.

**Boot 4.x gotchas found (add to the pile):** `@WebMvcTest` moved to
`org.springframework.boot.webmvc.test.autoconfigure` (new `spring-boot-starter-webmvc-test`
dependency); the slice no longer auto-includes security auto-config —
`ServletWebSecurityAutoConfiguration`/`SecurityFilterAutoConfiguration` (module
`spring-boot-security`) must be imported explicitly, and `spring-boot-security-test` is needed
for `@WithMockUser`/`csrf()` to apply to auto-configured MockMvc.

**Why:** First impressions and demoability: registration completes the self-service story, and
the audit console makes the tamper-evident chain *visible* — a security feature nobody can see
is a security feature that doesn't demo.

## 2026-07-07 (later still) — Tamper-evident audit log (hash chain)
**What:** Made the `auth_audit_event` trail *cryptographically* tamper-evident instead of
append-only-by-convention (chosen from a brainstorm of Phase-6+ value adds — beat out DPoP,
token exchange, and an admin dashboard because it's unique, security-deep, and fully verifiable
without Docker on this machine).
- **Migration V6:** `prev_hash`/`entry_hash` columns on `auth_audit_event` + single-row
  `audit_chain_head` anchor table (seeded at a 64-zero genesis hash).
- **`AuditHashChain`:** canonical SHA-256 encoding — domain-separation prefix, length-prefixed
  UTF-8 fields (no field-boundary ambiguity), null ≠ empty, timestamp as epoch second + nanos.
  `AuthAuditEvent.occurredAt` is now truncated to **microseconds** so the hashed value survives
  the Postgres `TIMESTAMPTZ` round-trip (nanos would silently break re-verification).
- **`AuditService`:** each append locks `audit_chain_head` (`SELECT … FOR UPDATE` via
  `@Lock(PESSIMISTIC_WRITE)`), hashes the event against the head's last hash, inserts, advances
  the head. Serializing appends is a deliberate trade — auth events are low-volume and the chain
  can never fork.
- **`AuditChainVerifier`** + **`GET /api/admin/audit/verify`** (ROLE_ADMIN, covered by the
  existing `/api/admin/**` rule): re-walks the chain from genesis in id order, recomputes every
  hash, checks the head anchor. Detects row rewrite, mid-chain deletion, tail truncation, and
  unhashed-row injection; pre-V6 rows are reported as `legacyEvents`. Each verification is itself
  audited (`AUDIT_CHAIN_VERIFIED`).
- **Tests (no Docker needed):** `AuditHashChainTest` (4 — determinism, boundary ambiguity,
  null-vs-empty, every-field-matters) and `AuditChainTamperTest` (7 — simulates direct-DB attacks
  against an in-memory table: UPDATE, DELETE mid-chain, tail truncation, forged insert, legacy
  tolerance). Auth-server suite 16/16 green on JDK 24.

**Why:** The entity Javadoc *claimed* tamper evidence but anyone with DB access could silently
rewrite history — a real STRIDE Tampering/Repudiation gap now closed, and a memorable
"certificate-transparency-style log, no blockchain required" interview talking point.

**Honest residual risk (also in THREAT_MODEL):** an attacker who rewrites the head row *and* the
tail rows consistently can truncate undetected. Fix is exporting the head hash to an external
anchor (log pipeline / another store) — the verify endpoint returns it for exactly that purpose.

**Next session:** the Testcontainers ITs exercise the new append path against real Postgres in CI —
watch that run. Optional follow-ups: scheduled verification with a Prometheus gauge; log the head
hash periodically as a free external anchor.
**What:**
- **Helm chart** `deploy/helm/aegis`: a templated `workloads.yaml` loops the three services into
  Deployments + ClusterIP Services (DRY), a shared `ConfigMap` (issuer/OTLP/backing-service env) +
  DEV `Secret` (or `secrets.existingSecret` for real deploys), and a gateway-only `Ingress`.
  K8s liveness/readiness probes (Boot auto-enables the probe endpoints in-cluster), hardened pod
  securityContext (runAsNonRoot, drop ALL caps, no priv-esc, seccomp RuntimeDefault), Prometheus
  scrape annotations. **Verified with a downloaded Helm 3.16.3:** `helm lint` clean and
  `helm template` renders 9 objects; `existingSecret`/`ingress.enabled=false` conditionals work.
- **Remaining security tests:**
  - `RateLimitConfigTest.sameCallerGetsSameKeyRegardlessOfPathOrHeaders` — rate-limit bypass
    resistance (key derives from principal, not path/headers). **Passes locally.**
  - `RefreshTokenRotationIntegrationTest` — full Authorization-Code + PKCE flow against Testcontainers
    Postgres (form login w/ CSRF + session cookies via `java.net.http.HttpClient`, authorize→code,
    code→tokens), then asserts a refresh returns a *new* refresh token (rotation) and replaying the
    *old* one yields `400 invalid_grant`. **Compiles locally; runs in CI** (needs Docker).
- **Phase 6 storytelling:** Mermaid **system diagram** in `ARCHITECTURE.md`; a **"how I'd attack
  this"** red-team narrative in `THREAT_MODEL.md` (7 attacker moves → where each dies, + honest gaps);
  a ~5-min **demo script** `docs/DEMO.md`; README docs list + status refreshed.

**Why:** Close out the deliverable/hardening phase and add the interview-facing polish — a diagram,
an attacker narrative, and a runnable demo are what actually communicate the work.

**Decisions / notes:**
- The refresh-replay test is named `*IntegrationTest` so the **local test filter is now
  `!*IntegrationTest`** (was `!AuthServerIntegrationTest`) — two Testcontainers tests exist now.
- **Two hardening items deliberately NOT coded** (would be unrunnable/broken without live infra):
  moving the JWT signing key into **Vault transit/KMS** and switching Vault to **AppRole** auth.
  Both are designed and documented (ROADMAP + threat model) as the next hardening step.

**Verified here (JDK 24):** auth-server `test-compile` green (new IT compiles); gateway
`RateLimitConfigTest` 4/4 green; Helm lint + template clean. Not runnable locally (CI/infra):
`RefreshTokenRotationIntegrationTest` (Docker), the Actions workflow, Semgrep/Trivy, live deploy.

**Next session should:** live cloud deploy (any k8s + registry) to unlock the demo URL + recording;
then the Vault-transit signing-key migration.

---

## 2026-07-07 — Phase 5 (DevSecOps & delivery)
**What:**
- **Security tests (JWT attacks):** `TokenSecurityTest` (resource-demo) forges/mangles tokens the
  way an attacker would and asserts **401** every time: `alg=none` (signature stripping), token
  signed by an untrusted key, payload tampered after signing (scope escalation attempt), expired,
  wrong issuer, and malformed input — plus a positive control that a genuine token still gets 200.
  Added a scope-escalation case to the policy suite (`authz_test.test_read_scope_cannot_escalate_to_write`).
- **Multi-stage Dockerfiles** per service (Temurin 25 JDK build → JRE runtime): POM-first layer for
  dependency caching, non-root `aegis` user, `MaxRAMPercentage`, and a `/actuator/health` HEALTHCHECK.
  Added `.dockerignore` (keeps `target/`, `certs/`, secrets out of the build context).
- **SBOM:** declared the `cyclonedx-maven-plugin` bare so **Spring Boot's native SBOM** execution
  activates — writes `target/classes/META-INF/sbom/application.cdx.json` into each jar and serves it
  at the actuator `sbom` endpoint (exposed but left authenticated — a dependency list aids attackers).
- **CI:** `.github/workflows/ci.yml` (push/PR to main), least-privilege token, concurrency-cancel:
  build+test on JDK 25 (`mvnw verify` — runs the full suite *including* `AuthServerIntegrationTest`
  since GitHub's Linux runners have Docker for Testcontainers) with SBOM artifacts; OPA policy tests;
  Semgrep SAST; Trivy fs scan (vuln+secret+misconfig → SARIF to the Security tab); Trivy image scans
  of all three containers. CVE gating report-only for now (documented how to flip it to blocking).

**Why:** Phase 5 makes the platform *deliverable and provably hardened* — the security posture is now
enforced by tests and a pipeline, not just asserted in prose.

**Gotchas / decisions:**
- **Found and removed an abandoned, uncompilable stub** `security/TokenAttackTest.java` (truncated
  mid-Javadoc, never tracked in git) — it was breaking `testCompile`. Its intended cases are all
  covered by the new `TokenSecurityTest`; folded in the one it was missing (malformed token → 401).
- First tried a custom `cyclonedx` config with `makeAggregateBom`/`makeBom` bound to `package`;
  it double-executed and failed (exit 255) because **the Boot 4.1 parent already manages a cyclonedx
  execution**. Bare plugin declaration is the idiomatic fix and yields the Boot-native SBOM.
- Dockerfile HEALTHCHECK uses `/actuator/health` (always present + permitted); the readiness probe
  path isn't exposed outside Kubernetes by default.

**Verified here (JDK 24, `-Dmaven.compiler.release=24`):** `TokenSecurityTest` 7/7 green;
scope-escalation rego case added; native SBOM generation confirmed (169 components for the gateway);
full `package` (minus the Testcontainers IT) builds all modules + SBOMs.

**Could not run here:** the GitHub Actions workflow itself, `opa test`, Trivy/Semgrep, and Docker
image builds (no Docker / no OPA binary locally) — all wired and will run in CI.

**Next session should:** finish Phase 5 (K8s manifests / Helm chart + a cloud free-tier deploy;
rate-limit-bypass + refresh-replay tests), then Phase 6 (diagrams, demo recording, live URL).

---

## 2026-07-06 (later) — Phase 4 COMPLETE (mTLS + Vault secrets) + login-page redesign
**What:**
- **mTLS gateway↔resource-demo (service identity):** `scripts/gen-dev-certs.ps1`/`.sh` build a dev
  CA and CA-signed identities (server cert `CN=aegis-resource-demo` with `eku=serverAuth`, client
  cert `CN=aegis-gateway` with `eku=clientAuth`) into gitignored `certs/`. An opt-in **`mtls`
  profile** on both services wires them via Boot **SSL bundles**: resource-demo serves HTTPS with
  `client-auth: need`; the gateway's proxy HttpClient uses
  `spring.cloud.gateway.server.webflux.httpclient.ssl.ssl-bundle` (property name verified against
  the SCG 5.0.2 jar's config metadata) and the route flips to `https://` via the new
  `aegis.routes.resource-demo-uri` placeholder.
- **Secrets via Vault:** docker-compose `vault` (dev mode, root token `aegis-dev-token`, DEV ONLY);
  auth server gained `spring-cloud-starter-vault-config` and a **`vault` profile**
  (`spring.config.import: vault://`, KV v2 `secret/aegis-auth-server`), inert by default
  (`spring.cloud.vault.enabled: false`). Datasource creds are now env-first
  (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`) so nothing requires editing YAML. Seed via
  `scripts/vault-seed.ps1`/`.sh` (plain KV v2 REST, no vault CLI needed).
- **Login page redesign** (the first thing anyone sees of the app): split-screen product page —
  left brand panel with the zero-trust story ("Trust nothing. Verify everything."), animated CSS
  aurora, shield mark, and three feature cards (verify-every-hop, policy-as-code, hardened-by-default);
  right glassmorphism sign-in card with proper focus states, `autocomplete` hints, a11y roles, and
  a DEV-marked demo-credentials hint. Deliberately zero JS / zero CDN (CSP-friendly, offline-safe);
  responsive (brand panel collapses under 900px).

**Verified here:**
- Cert script runs clean (keytool, JDK 24). **Live mTLS test:** resource-demo booted with the
  `mtls` profile (Tomcat on 8081 **https**); connection **without** a client cert → TLS handshake
  rejected; with the gateway's CA-signed cert → `200 {"status":"UP"}` from /actuator/health.
- Full build + runnable test suite green with the Vault starter resolved from the Oakwood BOM.
- Windows gotcha: the bundled curl (Schannel) won't send a P12 client cert — used .NET
  `HttpWebRequest.ClientCertificates` for the positive test.

**Could not run here:** live Vault round-trip (Docker down) — wired, will light up with
`docker compose up -d` + `scripts/vault-seed.ps1` + `SPRING_PROFILES_ACTIVE=vault`.

**Notes / deferred (tracked in ROADMAP):** signing-key private material is still PEM in Postgres
(next: Vault transit/KMS); Vault dev-token auth and plain HTTP are DEV ONLY; JWKS traffic and
Redis/Postgres links are not yet mTLS.

**Next session should:** Phase 5 (GitHub Actions CI with dependency/container scanning + SAST,
SBOM, Dockerfiles/Helm, security tests — token tampering, `alg=none`, scope escalation).

---

## 2026-07-06 — Phase 4 (observability & resilience — first slice)
**What:** Took the observability + resilience core of Phase 4; mTLS and Vault/secrets are the two
remaining Phase-4 items (still unchecked in ROADMAP).
- **Distributed tracing** across all three services: added `micrometer-tracing-bridge-otel` +
  `opentelemetry-exporter-otlp`. Traces export over OTLP to `${AEGIS_OTLP_ENDPOINT:http://localhost:4318/v1/traces}`.
  Sampling is 1.0 in dev. Because Micrometer Tracing is now on the classpath, Boot auto-adds the
  `[app,traceId,spanId]` correlation fields to console logs, and trace context propagates gateway →
  resource-demo so spans stitch into one end-to-end trace.
- **Metrics:** added `micrometer-registry-prometheus` so `/actuator/prometheus` actually serves;
  every meter is tagged `application=<service>`. Permitted `/actuator/prometheus` + `/actuator/info`
  (alongside health) in all three security configs so the collector can scrape without a JWT (prod
  note in-code: bind the management port to an internal network instead).
- **Structured JSON logging:** a `prod` Spring profile switches console output to ECS JSON
  (Boot 4.1 native `logging.structured.format.console: ecs`) — 12-factor stdout logs carrying
  traceId/spanId, no tokens. Dev stays human-readable.
- **Per-caller rate limiting (gateway):** new `principalOrClientKeyResolver` (`resilience/RateLimitConfig`)
  keys the Redis token bucket on `user:<sub>` or `client:<client_id>` instead of per-route, so one
  caller can't drain everyone's budget. Wired via `key-resolver` on the `RequestRateLimiter` filter.
- **Circuit breaker (gateway):** Resilience4j `CircuitBreaker` filter on the resource-demo route
  (`spring-cloud-starter-circuitbreaker-reactor-resilience4j`) with a local `forward:/fallback/resource-demo`
  → `FallbackController` returning a clean 503. Tuned under `resilience4j.circuitbreaker`/`.timelimiter`.
- **Infra:** docker-compose now runs Jaeger (OTLP in, UI :16686), Prometheus (:9090, scrapes the
  three services via `host.docker.internal`) and Grafana (:3000, datasources auto-provisioned).
  New files: `observability/prometheus.yml`, `observability/grafana/provisioning/datasources/…`.

**Why:** "Production hardening & observability" — make the platform debuggable (traces), measurable
(metrics), operable (JSON logs correlated to traces), and resilient (per-caller fairness + failing
fast on a sick downstream) before layering on mTLS and a real secrets manager.

**Verified:** `JAVA_HOME=jdk-24 mvnw -Dmaven.compiler.release=24 -Dtest='!AuthServerIntegrationTest' test`
→ BUILD SUCCESS. Gateway context loads with the tracing/circuit-breaker/rate-limit beans; existing
edge-auth and whoami tests still pass.

**Could not run here:** live trace export to Jaeger, Prometheus scrape, and the Grafana stack (Docker
down in this environment) — all wired and will light up with `docker compose up -d`.

**Next session should:** finish Phase 4 — **mTLS** gateway↔services (service identity, keystores +
a dev CA, a `prod`/`mtls` profile) and **secrets** out of `application.yml` into Vault or a cloud
secret manager (start with the Postgres creds + the signing-key material called out in Phase 2).

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

## 2026-08-31 — Cinematic hero on the demo console

**What:** Replaced the gradient header of `aegis-gateway/src/main/resources/static/index.html`
with a full-viewport hero built on the supplied artwork (`asset/BG2.png`, copied to
`static/assets/img/hero-bg.png` — `/assets/**` is already permitted in `GatewaySecurityConfig`).
Nav bar (logo · section links · Sign In + outlined "Your Account"), an oversized light-weight
"Aegis" wordmark with the "Zero trust, proven live." line, an outlined pill CTA plus a plain
text link, and a three-column hero footer (caption · description · "[Scroll to Explore]").

**Why:** The console is the first thing a reviewer sees; it now reads as a product landing page
instead of a dashboard. The theme is carried through the rest of the page — base background
darkened to `#070b14` so the hero fades into the body with no seam.

**Notes:**
- Three scrims (left, top, bottom) keep the nav, headline, and footer legible over the artwork
  without washing it out; the bottom one lands exactly on `--bg`.
- The four JS-populated link ids (`link-login`/`link-account`/`link-audit`/`link-jwks`) moved
  into the nav unchanged, so `applyConfig()` still wires them.
- Section anchors `#identity` / `#scenarios` added for the hero CTAs; `scroll-behavior:smooth`.
- The image is ~2.3 MB PNG. It is preloaded, but converting it to WebP/AVIF would cut the
  hero's LCP substantially — worth doing before the deployment is published.

## 2026-08-31 (later) — One artwork behind the whole console

**What:** The hero background became the page background. `body::before` paints the artwork
`position:fixed` behind the entire document (`cover`, so it fills any viewport without
distortion) and `body::after` lays a light global scrim over it. The header no longer carries
its own copy of the image — it just adds a left/top scrim for the nav and headline, so the
hero shows the artwork almost bare.

Everything below the hero now lives in `<main class="console">`, a sheet of dark glass
(`rgba(7,11,20,.55)` at its top edge deepening to `.93`) that the console rides on: the
sections, panels, and scenario cards render on top of the image, and the valley is still
visibly underneath. Panels and scenario cards got `backdrop-filter: blur(8px)`; the code
blocks and the sticky log-table header changed from opaque `#0b1120` to translucent inks so
they read as insets on the glass rather than holes punched through it. `body` is a flex
column with `.console{flex:1 0 auto}` so the sheet reaches the bottom of the viewport even
when the content is short — otherwise a bare band of artwork hung below the last section.

**Fixed:** the previous entry's `#scenarios` anchor collided with the `<div class="grid"
id="scenarios">` that the JS fills with scenario cards — `getElementById` returned the
heading, so the cards would have rendered into the `<h2>`. The anchor is now `#probe`.

**Note on verification:** the preview pane fails to paint scrolled content on this page
(hit-testing confirms the DOM and stacking are correct; it is a capture artifact, not a
layout bug). Verified instead by shrinking the hero and using a tall viewport so the whole
document sits above the fold.
