# Aegis — Technology Stack Guide

> **Who this is for:** you (the developer) — as a study reference and interview-prep document.
> For every technology in the platform it answers four questions:
> **What is it? · Where is it used in this repo? · Why did we pick it? · What is its USP
> (the one thing it does better than the alternatives)?**
>
> Related docs: [ARCHITECTURE.md](ARCHITECTURE.md) (request flow + diagrams),
> [ROADMAP.md](ROADMAP.md) (plan of record), [THREAT_MODEL.md](THREAT_MODEL.md) (what we defend against).

---

## 1. The 30-second big picture

Aegis is a **zero-trust identity & access platform** made of three services plus supporting infrastructure:

```
Browser/Client ──► aegis-gateway (:8080) ──► aegis-resource-demo (:8081)
                      │        │
                      │        └──► OPA (:8181)  "may this request proceed?"
                      │
                      ▼
              aegis-auth-server (:9000)  "who are you?" (issues JWTs)
                      │
                      ▼
              PostgreSQL (:5432)  users · clients · signing keys · audit chain
              Redis (:6379)       rate-limit counters
              Vault (:8200)       secrets (opt-in profile)
              Jaeger/Prometheus/Grafana  traces + metrics
```

Zero-trust in one sentence: **no request is trusted because of where it came from — every request
proves who it is (JWT) and every boundary re-checks it (gateway *and* downstream service), and
every action is authorized against policy (OPA) and audited (hash-chained log).**

Each technology below exists to serve exactly one part of that sentence.

---

## 2. Language, build & project structure

### Java 25 (LTS)

| | |
|---|---|
| **Where** | Everything — all three modules compile with `--release 25` (parent [pom.xml](../pom.xml)) |
| **Why** | The current LTS at project start (July 2026). LTS matters because a security product signals "I make production-safe choices"; a non-LTS release would be end-of-life in 6 months. |
| **USP** | Long-term support + modern language features (records for DTOs/policy inputs, pattern matching, virtual threads available if ever needed) without the churn of interim releases. |
| **Interview angle** | "I locked versions deliberately and documented the choice — Boot 4.1 supports Java 17–26, so 25 was the newest LTS in that window." |
| **Gotcha** | If only an older JDK is installed, build with `-Dmaven.compiler.release=<n>` — the code intentionally uses no Java-25-only syntax. |

### Maven (multi-module) + Maven Wrapper

| | |
|---|---|
| **Where** | Parent [pom.xml](../pom.xml) aggregates `aegis-auth-server`, `aegis-gateway`, `aegis-resource-demo`; `./mvnw` checked in. |
| **Why** | Three services share one version catalog (Boot parent + Spring Cloud BOM) and one CI build. Maven over Gradle: convention-heavy, declarative, and the *de facto* standard in enterprise Java — the audience this portfolio targets. |
| **USP** | The BOM (`dependencyManagement`) import model: one line pins the entire compatible dependency universe (Boot parent 4.1.0 + Spring Cloud 2025.1.2), so module POMs declare starters **without versions** and can never drift apart. |
| **Alternative** | Gradle — faster and more flexible, but flexibility is a liability in a portfolio meant to demonstrate discipline; Maven's rigidity *is* the feature here. |

---

## 3. Core frameworks

### Spring Boot 4.1 (on Spring Framework 7)

| | |
|---|---|
| **Where** | All three services. |
| **Why** | Production-grade defaults for everything this project needs: security, data access, actuator health/metrics, native structured logging, SSL bundles for mTLS, Testcontainers integration, buildable SBOMs. Choosing the newest major version was deliberate: it forces reading current reference docs instead of copy-pasting Stack Overflow answers written for Boot 3.x. |
| **USP** | Auto-configuration + starters: `spring-boot-starter-oauth2-authorization-server` turns ~40 pages of OAuth2 spec plumbing into a dependency plus a config block, while still letting you override every piece (which we do — custom JWK source, token customizer, MFA provider). |
| **Gotchas we hit (documented in CLAUDE.md)** | Boot 4.1 ships **Jackson 3** (`tools.jackson`) but Spring Security 7's serialization still needs Jackson 2 → explicit `jackson-databind` pin. `@WebMvcTest` moved to `spring-boot-starter-webmvc-test` and no longer auto-imports security config. `applyDefaultSecurity()` removed in Security 7. |

### Spring Security 7

| | |
|---|---|
| **Where** | Every module. Auth server: form login + MFA (`MfaAuthenticationProvider`), lockout, `/admin/**` role rules. Gateway + resource-demo: OAuth2 **resource server** JWT validation. |
| **Why** | It is the reference implementation of security on the JVM. Rolling your own session/CSRF/password handling is the classic security anti-pattern — the project hand-rolls only what it means to *teach* (TOTP math), and delegates everything else. |
| **USP** | The filter-chain model + lambda DSL: security is a composable, testable pipeline. We slot a custom `AuthenticationProvider` (MFA/OTP check) into the standard chain without forking any framework behavior. |
| **Key design point** | The same JWT is validated **twice** — once at the gateway, again inside resource-demo. That redundancy is not waste; it's the zero-trust "defense in depth" claim made executable. |

### Spring Authorization Server (SAS)

| | |
|---|---|
| **Where** | `aegis-auth-server` — the whole module is built around it. |
| **Why** | The only actively-maintained, spec-complete OAuth2.1/OIDC *server* framework in the Spring ecosystem (replaces the deprecated Spring Security OAuth). It gives standards-compliant endpoints (`/oauth2/authorize`, `/oauth2/token`, `/.well-known/...`, JWKS) while leaving storage and token contents pluggable. |
| **USP** | **Extensibility at every seam.** We replaced: client storage (`JpaRegisteredClientRepository` → Postgres), user storage (`JpaUserDetailsService`), key management (`RotatingJwkSource` → rotatable keys in Postgres, all active keys in JWKS), and token contents (`AegisJwtCustomizer` stamps `kid` + `roles`). None of that required patching the framework. |
| **Alternative (the big interview question)** | **Keycloak** — "why didn't you just deploy Keycloak?" Answer: Keycloak is the right choice when you need an IdP *product*; building on SAS demonstrates you understand what's inside one — token issuance, PKCE, refresh rotation, key rotation, consent. For a portfolio, operating a black box proves little. |

### Spring Cloud Gateway (WebFlux / reactive)

| | |
|---|---|
| **Where** | `aegis-gateway` — starter `spring-cloud-starter-gateway-server-webflux` (renamed in Spring Cloud 2025.x; the old `spring-cloud-starter-gateway` is deprecated). |
| **Why** | The gateway is the zero-trust **Policy Enforcement Point**: it must authenticate, rate-limit, ask OPA for a decision, and proxy — per request, at the edge. A reactive, non-blocking runtime (Netty + Project Reactor) is the correct shape for an I/O-bound proxy: threads are never parked waiting on downstream calls. |
| **USP** | **Filters are code, not config.** `PolicyEnforcementFilter` is a plain `GlobalFilter` bean that calls OPA via `WebClient` and fails closed. In Nginx/Kong that logic would live in Lua/plugins outside your language, test framework, and debugger. |
| **Alternatives** | Nginx/Kong/Envoy — more performant as pure proxies, but custom authz logic becomes second-class. Spring Cloud Gateway keeps the PEP in Java, unit-testable, with the same observability stack as everything else. |
| **Consequence** | The gateway is the one module written reactively (`Mono`/`Flux`) — a deliberate contrast with the servlet-based auth server, and a talking point on when to choose each model. |

### Spring Data JPA (Hibernate)

| | |
|---|---|
| **Where** | `aegis-auth-server` only — users, roles, registered clients, signing keys, audit events. |
| **Why** | The auth server's data is classic relational CRUD with transactions (e.g., the audit append must lock the chain head — pessimistic `PESSIMISTIC_WRITE` locking is one annotation). JPA earns its complexity here. The gateway and resource-demo deliberately have **no database** — statelessness is a zero-trust property. |
| **USP** | Repository abstraction + transaction/locking semantics integrated with Spring's `@Transactional`. The hash-chain integrity guarantee ("appends lock the head, so the chain can't fork") is *built on* JPA pessimistic locking. |
| **Discipline applied** | `open-in-view: false`, constructor injection, Flyway owns the schema (Hibernate never DDLs). |

### Thymeleaf (server-rendered UI)

| | |
|---|---|
| **Where** | Auth server pages: login (with OTP field), `/register`, `/account`, `/account/mfa`, `/admin/audit`. Shared design system in `static/css/aegis.css` + `templates/fragments/ui.html`. |
| **Why** | An auth server's pages (login, consent, MFA) **must** be server-rendered — they're part of the OAuth2 redirect dance and must work without a JS app. Zero JS/CDN dependencies is also a security posture: no supply-chain surface on the most sensitive pages in the system. |
| **USP** | Natural templating: the HTML files are valid HTML you can open directly, and fragments give component reuse without a build pipeline. Integrates natively with Spring Security (CSRF tokens, `sec:` attributes). |
| **Alternative** | React/SPA — wrong tool: the login page is *inside* the OAuth2 flow, and an SPA would need its own token handling, CORS, and a bundler for what is fundamentally five forms. |

---

## 4. Data & state

### PostgreSQL 18

| | |
|---|---|
| **Where** | Auth server's only datastore (docker-compose). Tables: `app_user`, `app_user_role`, registered clients, `signing_key`, `auth_audit_event`, `audit_chain_head`. |
| **Why** | Identity data is the crown jewels — it needs ACID transactions, row locking, and constraints. Postgres is the default serious choice: open-source, battle-tested, superb consistency guarantees. |
| **USP** | Transactional integrity with real locking semantics — the tamper-evident audit chain depends on `SELECT ... FOR UPDATE` (pessimistic write on the single-row chain head) to guarantee linearized appends. A document store cannot give you that guarantee this cheaply. |
| **Detail worth knowing** | `TIMESTAMPTZ` stores microsecond precision — Java `Instant` has nanos, so `occurredAt` is truncated to micros **before** hashing, or the hash would break on the DB round-trip. Real-world lesson: hash what you *store*, not what you *have*. |

### Redis 8

| | |
|---|---|
| **Where** | Gateway rate limiting (`spring-boot-starter-data-redis-reactive` + `RequestRateLimiter` filter). |
| **Why** | Rate-limit state must be **shared and fast**: if the gateway scaled to 3 replicas, an in-memory counter would triple every client's budget. Redis gives atomic, sub-millisecond counters visible to all instances. |
| **USP** | Atomic operations executed server-side (the token-bucket is a Lua script running inside Redis) — no read-modify-write races, no locks in application code. |
| **Design point** | The limiter key is `principalOrClientKeyResolver` (per-user / per-client, **not** per-IP or per-path) — and `RateLimitConfigTest` proves the key can't be evaded by path or header variation. The technology is standard; the tested key-design is the portfolio value. |

### Flyway

| | |
|---|---|
| **Where** | `aegis-auth-server/src/main/resources/db/migration` — V1…V6 (V6 = audit hash chain). |
| **Why** | The schema is code: versioned, reviewed, reproducible. `hibernate.ddl-auto` is a toy; a security product must be able to say exactly what its schema was on any date. |
| **USP** | Plain-SQL, linear, checksummed migrations — trivially auditable (an ORM-generated diff is not), and the migration history itself documents how the system evolved (V6 alone tells the audit-chain story). |
| **Alternative** | Liquibase — more abstraction (XML/YAML changelogs, rollbacks); Flyway's raw SQL is simpler and matches the "schema is an auditable artifact" philosophy. |

---

## 5. Authorization & policy

### Open Policy Agent (OPA) + Rego

| | |
|---|---|
| **Where** | `opa` service in docker-compose (:8181) loads [policies/](../policies/); gateway calls it per-request via `OpaPolicyDecisionPoint` (`aegis.authz.opa.*`). |
| **Why** | This is the **PDP/PEP split** — the architectural heart of Phase 3. Authorization rules (RBAC roles, `demo.read`/`demo.write` scopes, resource ownership, time-of-day write window) live *outside* application code, in a policy engine, so they can change, be tested, and be audited independently of deployments. |
| **USP** | **Policy-as-code with its own test framework.** `authz_test.rego` runs with `opa test policies/` — authorization rules have unit tests, in CI, that block scope-escalation regressions. No if-statement scattered across controllers can offer that. |
| **Fail-closed** | If OPA is down or errors, the gateway **denies**. Availability is sacrificed for integrity — the correct trade for a PEP, and an explicit talking point. |
| **Alternatives** | Spring method security (`@PreAuthorize`) — scatters policy through code, no independent testing/audit. Casbin — simpler but less expressive and a much smaller ecosystem. OPA is the CNCF-graduated industry standard (used by Kubernetes admission control, Istio, Terraform). |

### JWT (JSON Web Tokens) — via Nimbus JOSE (transitive)

| | |
|---|---|
| **Where** | The currency of the whole platform: auth server signs; gateway and resource-demo verify against the JWKS endpoint. |
| **Why** | Stateless verification: any service can validate a token with only the public key — no shared session store, no per-request call back to the auth server. That's what makes "re-validate at every boundary" cheap enough to actually do. |
| **USP** | Asymmetric signing (RS256): the private key exists *only* in the auth server; verifiers hold public keys only, so compromising a downstream service leaks nothing that can mint tokens. |
| **Hardening in this repo** | Short-lived access tokens + rotating refresh tokens (replay of a rotated refresh token → `invalid_grant`, proven by `RefreshTokenRotationIntegrationTest`); `TokenSecurityTest` rejects `alg=none`, untrusted keys, tampered payloads, expired tokens, wrong issuer; multiple active keys published in JWKS so rotation causes zero downtime. |

### TOTP / MFA (RFC 6238, hand-rolled) + ZXing

| | |
|---|---|
| **Where** | Auth server: `MfaEnrollmentService`, `MfaAuthenticationProvider`, `/account/mfa` page; `zxing:core` renders the `otpauth://` URI as an inline SVG QR (`QrSvgRenderer`). |
| **Why hand-rolled TOTP** | Deliberate exception to "don't roll your own": TOTP is ~30 lines of well-specified HMAC math, and implementing it demonstrates understanding of what Google Authenticator actually does. The QR encoding, by contrast, is presentation — so that uses the battle-tested library. Knowing **which** wheel to reinvent is the skill on display. |
| **ZXing USP** | Zero transitive dependencies, and we render the bit-matrix to SVG ourselves — no imaging stack (AWT) needed, keeps the container slim. |
| **Safety rule** | Two-step activation: the secret is stored *disabled* and MFA turns on only after a code verifies — you cannot lock yourself out by enrolling. |

### HashiCorp Vault

| | |
|---|---|
| **Where** | docker-compose (:8200, dev mode); auth server `vault` profile imports config via `vault://` (KV v2, `spring-cloud-starter-vault-config`); inert unless the profile is on. |
| **Why** | Secrets don't belong in files or env dumps. Vault demonstrates the production pattern: application pulls secrets at boot from a secret manager with its own audit log and access control. |
| **USP** | Dynamic/centralized secrets with lease-based lifecycle — and a growth path already designed in ROADMAP: move JWT signing into **Vault transit** so the private key *never exists in application memory at all*. |
| **Honest caveat (tracked)** | Currently dev-mode, token auth, plain HTTP — the follow-ups (AppRole, TLS) are documented in ROADMAP, which is itself the point: known shortcuts are tracked, not hidden. |

### mTLS (Spring Boot SSL bundles)

| | |
|---|---|
| **Where** | Opt-in `mtls` profile: resource-demo requires a client cert (`client-auth: need`); gateway presents a CA-signed cert via SSL-bundle-configured HTTP client. Dev CA from `scripts/gen-dev-certs.*`. |
| **Why** | Zero-trust applies to **services**, not just users: even inside the network, resource-demo only talks to callers holding a cert from our CA. Verified live: no cert → handshake rejected. |
| **USP of SSL bundles (Boot 3.1+/4.x)** | Named, reloadable TLS configuration in YAML — keystores/truststores become declarative config instead of `SSLContext` plumbing code. |

---

## 6. Observability & resilience

### Micrometer + OpenTelemetry (OTLP) — tracing

| | |
|---|---|
| **Where** | All three services: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` → collector at `AEGIS_OTLP_ENDPOINT` → **Jaeger** (:16686). |
| **Why** | A request crosses gateway → OPA → resource-demo; without distributed tracing, debugging a 403 means grepping three logs. With it, one trace shows the whole path with timings. |
| **USP** | Micrometer is the JVM's observability **facade** (like SLF4J for metrics/traces): instrument once, swap backends freely. OTLP is the vendor-neutral wire protocol — Jaeger today, Grafana Tempo or Datadog tomorrow, zero code change. |
| **Key detail** | Trace context **propagates** across HTTP hops (W3C traceparent), so gateway and downstream spans stitch into one trace automatically. |

### Prometheus + Grafana — metrics

| | |
|---|---|
| **Where** | `micrometer-registry-prometheus` in every service exposes `/actuator/prometheus` (tagged `application=<service>`); Prometheus (:9090) scrapes; Grafana (:3000) dashboards. Config in `observability/`. |
| **Why** | Pull-based metrics are the cloud-native standard: services expose, the collector scrapes — services need zero knowledge of the monitoring system. |
| **USP** | Prometheus: the dimensional data model + PromQL (e.g. rate-limit rejections per client per minute is one query). Grafana: the de facto visualization layer over it. |

### Structured logging (ECS JSON, Boot-native)

| | |
|---|---|
| **Where** | `prod` profile → ECS-format JSON on stdout, with `traceId`/`spanId` in every line (Boot 4.1 native structured logging — no logstash-encoder dependency). Dev stays human-readable. |
| **Why** | JSON logs are machine-parseable (Elastic/Loki-ready), and embedding trace IDs links logs ↔ traces: from any log line you can pull the full request trace. |
| **Rule** | Never log tokens or secrets — enforced as a convention and worth stating in interviews. |

### Resilience4j (circuit breaker)

| | |
|---|---|
| **Where** | Gateway: `spring-cloud-starter-circuitbreaker-reactor-resilience4j`, `CircuitBreaker` on the resource-demo route with a local 503 fallback (`com.aegis.gateway.resilience`). |
| **Why** | If resource-demo hangs, without a breaker every gateway request waits for the timeout — threads/connections pile up and the *gateway* dies too (cascading failure). The breaker fails fast and gives the downstream room to recover. |
| **USP** | Lightweight, functional, designed for Reactor — decorates a `Mono` without AOP or an agent. (Netflix Hystrix, the ancestor, is in maintenance mode; Resilience4j is its official successor.) |

---

## 7. Testing

### JUnit 5 + Spring Test (slices) + Testcontainers

| | |
|---|---|
| **Where** | Every module. Highlights: `TokenSecurityTest` (six negative JWT cases + positive control), `RefreshTokenRotationIntegrationTest` (full auth-code+PKCE flow against **Testcontainers Postgres**), `AuditChainTamperTest`/`AuditHashChainTest` (tamper detection, no Docker needed), `RateLimitConfigTest` (limiter-key evasion), `UiPagesRenderTest` (`@WebMvcTest` every page), `reactor-test` for gateway filters, `opa test` for policies. |
| **Why Testcontainers** | An H2-based "integration test" of an auth server proves nothing — locking behavior, `TIMESTAMPTZ` semantics, and Flyway SQL are Postgres-specific. Testcontainers runs the **real** database in a throwaway Docker container per test run. |
| **Testcontainers USP** | Production-parity dependencies with zero shared test infrastructure — the test declares `postgres:18`, gets a fresh instance, and Boot 4.x wires the datasource via `@ServiceConnection`. |
| **The philosophy (biggest differentiator of this repo)** | **Security claims are tests, not comments.** "Refresh replay is rejected", "the chain detects mid-chain deletes", "scope escalation is blocked" — each is an executable assertion that runs in CI. |

---

## 8. Delivery & DevSecOps

### Docker + docker-compose

| | |
|---|---|
| **Where** | [docker-compose.yml](../docker-compose.yml): postgres:18, redis:8, OPA, Vault, Jaeger, Prometheus, Grafana. Multi-stage `Dockerfile` per service (non-root user, healthcheck) + `.dockerignore`. |
| **Why** | One command (`docker compose up -d`) reproduces the full environment — seven infrastructure services — on any machine. Multi-stage builds keep runtime images JRE-only (no Maven, no source). |
| **USP** | Multi-stage + non-root + healthcheck is the container-hardening triad: small attack surface, least privilege, orchestrator-visible liveness. |

### GitHub Actions (CI)

| | |
|---|---|
| **Where** | `.github/workflows/ci.yml`. |
| **Why** | Every push builds, tests (full `mvnw verify` incl. Testcontainers ITs — GitHub runners have Docker), runs `opa test`, and security-scans. A green badge on the README is the cheapest possible proof the project actually works. |
| **Pipeline stages** | Build+test (JDK 25) → OPA policy tests → **Semgrep** (SAST: scans *our code* for vulnerable patterns) → **Trivy** fs + image (SCA: scans *dependencies and the container* for known CVEs), both uploading SARIF to the GitHub Security tab, currently report-only. |
| **Semgrep vs Trivy in one line** | Semgrep finds bugs you wrote; Trivy finds bugs you inherited. A DevSecOps pipeline needs both. |

### CycloneDX SBOM

| | |
|---|---|
| **Where** | Parent POM activates Boot's managed `cyclonedx-maven-plugin` → `META-INF/sbom/application.cdx.json` in every jar, served live via the actuator `sbom` endpoint. |
| **Why** | Post log4shell, "what exact libraries are running in prod?" must be answerable in seconds. SBOMs are increasingly a compliance requirement (US EO 14028, EU CRA). |
| **USP** | Boot-native generation: the SBOM is produced *by the build* and shipped *inside the artifact* — it can't go stale or get lost, and a running instance reports its own bill of materials. |

### Helm + Kubernetes

| | |
|---|---|
| **Where** | `deploy/helm/aegis` — 3 Deployments/Services, ConfigMap/Secret split, gateway Ingress, liveness/readiness probes, hardened `securityContext` (lint/template verified). |
| **Why** | Kubernetes is where systems like this actually run; the Helm chart shows the services are deployment-ready, with the config/secret separation and probes production reviewers look for. |
| **USP** | Helm = templated, versioned, parameterized releases — one chart, N environments via `values.yaml`, and `helm template` makes the rendered manifests reviewable in CI without a cluster. |

---

## 9. One-page decision map

| Technology | Lives in | One-line "why" |
|---|---|---|
| Java 25 LTS | everywhere | modern features on a supported base |
| Maven multi-module | repo root | one BOM, three services, zero version drift |
| Spring Boot 4.1 / Framework 7 | all services | production defaults, everything overridable |
| Spring Authorization Server | auth-server | standards-complete OAuth2/OIDC, every seam pluggable |
| Spring Security 7 | all services | never hand-roll session/CSRF/password handling |
| Spring Cloud Gateway (WebFlux) | gateway | non-blocking PEP; authz filter is testable Java |
| Spring Data JPA | auth-server | transactions + pessimistic locking for the audit chain |
| Thymeleaf | auth-server UI | login/MFA pages must be server-rendered, zero JS supply chain |
| PostgreSQL 18 | auth-server | ACID + row locking for identity data and the hash chain |
| Flyway | auth-server | schema as auditable, versioned SQL |
| Redis 8 | gateway | atomic shared rate-limit counters across replicas |
| OPA + Rego | gateway ↔ :8181 | policy-as-code with its own tests; PDP/PEP split; fail-closed |
| JWT (RS256) | token flow | stateless verification at every boundary |
| TOTP (hand-rolled) + ZXing | auth-server | show the MFA math; outsource the QR pixels |
| Vault | auth-server (`vault` profile) | secrets out of files; path to transit-signing |
| SSL bundles / mTLS | gateway ↔ resource-demo | zero-trust between services, not just users |
| Micrometer + OTel + Jaeger | all services | one trace across every hop, vendor-neutral |
| Prometheus + Grafana | all services | pull-based dimensional metrics, standard dashboards |
| ECS JSON logging | `prod` profile | machine-parseable logs linked to traces |
| Resilience4j | gateway | fail fast instead of cascading failure |
| Testcontainers | auth-server ITs | integration tests against the real Postgres |
| GitHub Actions | `.github/workflows` | build, test, policy-test, and scan every push |
| Semgrep + Trivy | CI | SAST (our code) + SCA (our dependencies/images) |
| CycloneDX SBOM | every jar | the artifact carries its own bill of materials |
| Docker + Helm | `deploy/` | reproducible env; production-shaped K8s packaging |

---

## 10. The "why not X?" cheat sheet (interview prep)

- **Why not Keycloak/Auth0?** They're the right *product* choice; building on Spring Authorization
  Server proves I understand what's inside one — PKCE, refresh rotation, key rotation, JWKS,
  consent. The portfolio goal is demonstrated depth, not fastest time-to-login.
- **Why not Nginx/Kong as the gateway?** Their raw proxy throughput is higher, but the custom
  policy-enforcement logic (OPA call, fail-closed, principal-based rate-limit keys) would live in
  Lua/plugins — outside my language, tests, and debugger. Spring Cloud Gateway keeps the PEP as
  first-class, unit-tested Java.
- **Why not sessions instead of JWTs?** A shared session store couples every service to central
  state; signed JWTs let each boundary verify independently — which is precisely what makes
  "re-validate everywhere" affordable.
- **Why not `@PreAuthorize` for authz?** It scatters policy across controllers with no independent
  testing or audit. OPA centralizes policy as versioned, unit-tested code, deployable separately.
- **Why hand-roll TOTP but not QR encoding?** TOTP is 30 lines of specified HMAC math worth
  understanding; QR is presentation. Judgment about *which* wheel to reinvent is the skill.
- **Why reactive for the gateway but servlet for the auth server?** The gateway is a pure
  I/O-bound proxy — non-blocking wins. The auth server is transactional CRUD with JPA — blocking
  servlet + JPA is simpler and correct. Using both, deliberately, beats dogma either way.
- **Why Postgres for signing keys instead of Vault today?** Tracked shortcut (ROADMAP): keys are
  PEM-in-Postgres now, with the Vault-transit migration designed. Shipping with documented,
  prioritized debt is more senior than pretending there is none.

---

*Keep this document current: when a technology is added, replaced, or its role changes, update the
relevant section and the decision map. Last full revision: 2026-07-14.*
