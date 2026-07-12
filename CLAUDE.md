# Aegis Platform — Project Context (read me first)

> This file is loaded automatically at the start of every Claude Code session in this repo.
> It is the source of truth for **what this project is and where it stands**. Keep it current.

## What we're building

**Aegis** is a **Zero-Trust Identity & Access platform** — a portfolio-grade security product
designed to demonstrate senior-level backend engineering to top companies. It has three parts:

1. **`aegis-auth-server`** — OAuth2 / OpenID Connect Authorization Server. The single source of
   truth for identity. Issues short-lived signed JWTs (access tokens) and rotating refresh tokens.
2. **`aegis-gateway`** — A reactive, policy-enforcing API Gateway. The zero-trust **Policy
   Enforcement Point (PEP)**: authenticates every request against an Aegis-issued JWT, rate-limits,
   and forwards to downstream services. Fine-grained authorization (via a policy engine) comes in Phase 3.
3. **`aegis-resource-demo`** — A sample downstream microservice that re-validates the JWT locally
   (defense in depth) and echoes identity claims, proving end-to-end zero-trust.

The developer is a **Java dev with ~2 years of experience** using this as a flagship learning +
portfolio project. Favor clarity, correctness, security best practices, and production polish
(tests, CI/CD, observability, threat model) over feature count.

## Tech stack (locked versions — chosen July 2026, all latest)

| Technology | Version | Notes |
|---|---|---|
| Java | **25 (LTS)** | LTS; Boot 4.1 supports Java 17–26 |
| Spring Boot | **4.1.0** | Built on Spring Framework 7 |
| Spring Cloud | **2025.1.2 "Oakwood"** | First train with Boot 4.1 compatibility |
| Spring Authorization Server | via `spring-boot-starter-oauth2-authorization-server` | version managed by Boot BOM |
| Build tool | **Maven** (multi-module) | parent `pom.xml` aggregates the 3 modules |
| PostgreSQL | **18** | via Docker; Flyway owns the schema |
| Redis | **8** | gateway rate-limiting |
| Flyway | (Boot-managed) | DB migrations in `aegis-auth-server/.../db/migration` |
| Testcontainers | (Boot-managed) | integration tests against real Postgres |

> ⚠️ **Version note:** This stack is deliberately cutting-edge (Boot 4.x / Spring Framework 7 /
> Spring Security 7). Some APIs changed vs. the widely-documented Boot 3.x. If a build/API error
> appears, verify against the current reference docs before assuming the code is wrong.
> The **Gateway starter was renamed** in Spring Cloud 2025.x to
> `spring-cloud-starter-gateway-server-webflux` (old `spring-cloud-starter-gateway` is deprecated).

## Repository layout

```
aegis-platform/
├── pom.xml                     # parent / aggregator POM (versions, modules)
├── docker-compose.yml          # postgres:18 + redis:8 for local dev
├── CLAUDE.md                   # this file
├── README.md                   # human-facing quickstart
├── docs/
│   ├── ARCHITECTURE.md         # components, request flow, diagrams
│   ├── ROADMAP.md              # phased plan (Phase 1..6) — THE plan of record
│   ├── DEVELOPMENT_LOG.md      # append-only journal of what changed each session
│   └── THREAT_MODEL.md         # STRIDE-style threat model
├── policies/                   # Rego authorization policies (+ tests) loaded by OPA
├── aegis-auth-server/          # port 9000 — OAuth2/OIDC provider
├── aegis-gateway/              # port 8080 — PEP / reactive gateway (calls OPA PDP)
└── aegis-resource-demo/        # port 8081 — protected downstream service
```

## Ports
- Auth server: **9000** (issuer URI `http://localhost:9000`)
- Gateway: **8080** (public entry point)
- Resource demo: **8081** (only reached via the gateway in normal use)
- Postgres: 5432 · Redis: 6379 · OPA (Policy Decision Point): **8181**

## How to run (local)
```bash
docker compose up -d                     # start postgres + redis
./mvnw -pl aegis-auth-server spring-boot:run
./mvnw -pl aegis-gateway spring-boot:run
./mvnw -pl aegis-resource-demo spring-boot:run
```
The Maven wrapper (`mvnw`) is generated. **Build note:** the stack targets Java 25; if only an
older JDK is available you can compile/verify with `-Dmaven.compiler.release=<n>` (the code uses no
Java-25-only syntax). The auth-server integration test needs Docker (Testcontainers Postgres).

## Current status — Phases 1–5 DONE; Phase 6 (polish) IN PROGRESS
**Done (Phase 5 — DevSecOps & delivery):** security posture enforced by tests + a pipeline.
- **Security tests:** `TokenSecurityTest` rejects `alg=none`, untrusted-key sigs, tampered payloads,
  expired, wrong issuer, malformed (all → 401) + positive control; `authz_test.rego` blocks scope
  escalation; `RateLimitConfigTest` proves the limiter key can't be evaded by path/header variation;
  `RefreshTokenRotationIntegrationTest` drives the full auth-code+PKCE flow and asserts refresh
  rotation + replay rejection (`invalid_grant`).
- **CI:** `.github/workflows/ci.yml` — build+test on JDK 25 (full `mvnw verify` incl. Testcontainers
  ITs on the Docker runner), OPA policy tests, Semgrep SAST, Trivy fs + image scans (SARIF). Report-only CVE gate.
- **SBOM:** Boot-native CycloneDX per module (`META-INF/sbom/application.cdx.json`, `sbom` endpoint).
- **Containers + K8s:** multi-stage Dockerfiles (non-root, healthcheck) + `.dockerignore`; Helm chart
  `deploy/helm/aegis` (3 Deployments/Services, ConfigMap/Secret, gateway Ingress, probes, hardened
  securityContext — helm lint/template verified).

**Done (Phase 6 so far):** Mermaid system diagram (`ARCHITECTURE.md`), "how I'd attack this"
red-team narrative (`THREAT_MODEL.md`), demo script (`docs/DEMO.md`), README badges/quickstart.
- **Tamper-evident audit log:** `auth_audit_event` is SHA-256 **hash-chained** (migration V6:
  `prev_hash`/`entry_hash` + single-row `audit_chain_head` anchor). Appends lock the head
  (pessimistic write — the chain can't fork); `occurredAt` truncated to micros so hashes survive
  the TIMESTAMPTZ round-trip. `GET /api/admin/audit/verify` (ROLE_ADMIN) re-walks the chain,
  reports the first broken row, and is itself audited. Rewrite / mid-chain delete / reorder /
  inject / tail truncation all detected — `AuditChainTamperTest` + `AuditHashChainTest` (no
  Docker needed). Follow-up: anchor the head hash externally (logs / second store).
- **Web UI (shared design system):** styles extracted to `static/css/aegis.css` + brand fragments
  (`templates/fragments/ui.html`); **`/register`** page (HTML sibling of `POST /api/register`,
  same service → same policy/audit) and **`/admin/audit`** console (50 newest events, colored
  type chips, one-click chain verification with intact/broken banner + first-broken-row
  highlight). `/admin/**` requires ROLE_ADMIN. `UiPagesRenderTest` renders every page via
  @WebMvcTest — **Boot 4.x moved the slice** to `spring-boot-starter-webmvc-test` and security
  auto-config must be imported explicitly (+ `spring-boot-security-test` for `@WithMockUser`).
  `UiPreviewDumpTest` dumps rendered HTML to `target/ui-preview/` for visual review.
- **Account page + browser MFA enrollment:** **`/account`** (identity, role chips, MFA status)
  and **`/account/mfa`** (QR code of the `otpauth://` URI via zxing:core → inline SVG in
  `QrSvgRenderer`, manual-secret fallback, activation form). Shared `MfaEnrollmentService`
  backs both the pages and `/api/mfa/**`, preserving the two-step activation rule + audit.
**Remaining:** live cloud deploy + recording; hardening follow-ups (signing key → Vault transit,
Vault AppRole auth, mTLS for JWKS/datastore links) — all designed & documented, need infra to ship.

**Done (Phase 4):** observability, resilience, service identity, and secrets.
- **mTLS gateway↔resource-demo:** opt-in `mtls` profile on both services (Boot SSL bundles;
  resource-demo `client-auth: need`, gateway presents a CA-signed client cert via
  `...httpclient.ssl.ssl-bundle`). Dev CA + certs from `scripts/gen-dev-certs.ps1|sh` → `certs/`
  (gitignored). Verified live: no client cert → handshake rejected; gateway cert → 200.
- **Secrets:** Vault in docker-compose (:8200, dev mode); auth server `vault` profile imports
  `vault://` (KV v2 `secret/aegis-auth-server`, seed via `scripts/vault-seed.ps1|sh`); datasource
  creds are env-first (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`). Vault is inert unless the profile is on.
- **Login page** redesigned as a split-screen product page (brand story + glass sign-in card,
  zero JS/CDN, responsive) — the app's first impression.
- **Tracing:** all three services bridge Micrometer Tracing → OpenTelemetry → OTLP
  (`${AEGIS_OTLP_ENDPOINT:http://localhost:4318/v1/traces}`); trace context propagates edge→downstream.
- **Metrics:** `micrometer-registry-prometheus` serves `/actuator/prometheus` (tagged
  `application=<service>`); scrape + info endpoints are permitted for the collector.
- **Logs:** `prod` profile emits ECS JSON on stdout with traceId/spanId (Boot 4.1 native structured
  logging); dev stays human-readable. Never logs tokens.
- **Resilience (gateway):** per-user/per-client rate-limit key (`principalOrClientKeyResolver`) +
  Resilience4j `CircuitBreaker` on the resource-demo route with a local 503 fallback
  (`com.aegis.gateway.resilience`).
- **Infra:** docker-compose adds Jaeger (:16686), Prometheus (:9090), Grafana (:3000);
  config in `observability/`.

**Done (Phase 3):** the gateway is now a real PEP that authorizes, not just authenticates.
- **OPA (Rego)** is the Policy Decision Point — `opa` service in docker-compose (:8181) loads
  `policies/`. Policies are code: `policies/authz.rego` (+ `authz_test.rego`, run `opa test policies/`).
- **RBAC + ABAC:** admin role, `demo.read`/`demo.write` scopes, resource ownership on
  `/api/users/{id}`, and a time-of-day window for writes.
- **Gateway** `com.aegis.gateway.authz`: `PolicyEnforcementFilter` (reactive `GlobalFilter`) →
  `OpaPolicyDecisionPoint` (WebClient, **fail-closed**). Config under `aegis.authz.opa.*`.
- **Auth server** stamps a `roles` claim on user access tokens (`AegisJwtCustomizer`) for RBAC.

**Done (Phase 1):** multi-module skeleton; gateway (JWT auth + Redis rate-limit route); resource
demo `/api/demo/whoami`; docker-compose; docs; Maven wrapper; one integration test per module.

**Done (Phase 2):** nothing critical lives in memory anymore.
- **Clients** in Postgres via `JpaRegisteredClientRepository`; seeded `aegis-web-client` (Auth
  Code + PKCE) and confidential `aegis-service-client` (client_credentials, for smoke tests).
- **Users** in Postgres via `JpaUserDetailsService` (`app_user` / `app_user_role`).
- **Persistent, rotatable signing keys** (`signing_key` table + `RotatingJwkSource`): all active
  keys published in the JWKS, new tokens signed with the newest (`kid` stamped by
  `AegisJwtCustomizer`, which also adds a `roles` claim). Rotate via
  `POST /api/admin/keys/rotate` (ROLE_ADMIN).
- **Account lifecycle:** `POST /api/register` (password policy: ≥12 chars, letter+digit),
  **lockout** after 5 failed attempts for 15m (configurable under `aegis.security.lockout`).
- **MFA (TOTP, RFC 6238):** `POST /api/mfa/enroll` → `POST /api/mfa/activate`; login form has an
  `otp` field checked by `MfaAuthenticationProvider`.
- **Audit:** append-only `auth_audit_event` (login success/failure, lockout, MFA, key rotation,
  registration). Flyway migrations V1–V5 own the schema.

**Stack gotchas discovered (Boot 4.1 / Spring Security 7):** `applyDefaultSecurity()` was removed
(use the configurer + `http.with(...)`); Boot 4.1 ships **Jackson 3** (`tools.jackson`), so Spring
Security's Jackson2 serialization needs an explicit `com.fasterxml.jackson.core:jackson-databind`
dependency; Boot manages Testcontainers only via `${testcontainers.version}` (no default — pinned
in the parent POM); `@WebMvcTest` moved to `org.springframework.boot.webmvc.test.autoconfigure`
(dep `spring-boot-starter-webmvc-test`) and no longer auto-includes security auto-config — import
`ServletWebSecurityAutoConfiguration`/`SecurityFilterAutoConfiguration` explicitly and add
`spring-boot-security-test` for `@WithMockUser`/`csrf()` support.

**Known shortcuts still intentional (tracked in ROADMAP):**
- Demo creds `admin` / `changeit`; service-client secret `service-secret`; cert/Vault dev
  passwords (`changeit` / `aegis-dev-token`) — all DEV ONLY.
- Signing-key private material is still PEM in Postgres (follow-up: Vault transit / KMS).
- Vault runs dev-mode with token auth over plain HTTP; JWKS + Redis/Postgres links not yet mTLS.
- No CI yet (Phase 5).

## Conventions
- Java package root: `com.aegis.<module>` (e.g. `com.aegis.gateway`).
- Every "dev-only / replace-me" shortcut is marked with a comment referencing `docs/ROADMAP.md`.
- Config secrets are placeholders (`aegis`/`changeit`); real values come from env vars — never commit secrets.
- Prefer constructor injection, lambda security DSLs, `open-in-view: false`, Flyway-owned schema.

## Working agreement for Claude in this repo
1. **Before coding**, skim `docs/ROADMAP.md` to see the current phase and pick the next task from it.
2. **After any meaningful change**, append an entry to `docs/DEVELOPMENT_LOG.md` (date, what, why)
   and update the "Current status" section above if the phase state changed.
3. Keep security front-of-mind: short-lived tokens, least privilege, validate at every boundary,
   never log secrets/tokens.
