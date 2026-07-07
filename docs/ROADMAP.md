# Aegis — Roadmap (plan of record)

Phased plan. Each phase produces something demonstrable. Check items off as they land and log the
change in `DEVELOPMENT_LOG.md`. This is the file Claude should consult to pick the next task.

---

## Phase 1 — Scaffold & "hello, zero-trust" ✅ DONE
Goal: three services boot, and a JWT minted by the auth server is accepted by the gateway and the
downstream service end-to-end.

- [x] Multi-module Maven project (parent + 3 modules)
- [x] Auth server: OAuth2/OIDC with an in-memory client (Auth Code + PKCE) and in-memory user
- [x] Gateway: JWT authentication + one rate-limited route to the demo service
- [x] Resource demo: `/api/demo/whoami` returning JWT claims
- [x] docker-compose (Postgres 18 + Redis 8)
- [x] Docs: CLAUDE.md, README, ARCHITECTURE, ROADMAP, DEVELOPMENT_LOG, THREAT_MODEL
- [x] Add Maven wrapper (`mvnw`) so builds are reproducible
- [x] First smoke test: obtain a token (client_credentials on `aegis-service-client`) — covered by
      `AuthServerIntegrationTest`; resource server accepts a signed JWT (`WhoAmIControllerTest`)
- [x] One integration test per module (Testcontainers Postgres for the auth server; WebTestClient
      for the gateway; RANDOM_PORT + real RS256 JWT for the resource demo)

## Phase 2 — Real identity & persistence ✅ DONE
Goal: nothing critical lives in memory.

- [x] JPA-backed `RegisteredClientRepository` (clients in Postgres — `JpaRegisteredClientRepository`;
      seeded `aegis-web-client` + confidential `aegis-service-client`)
- [x] JPA-backed `UserDetailsService` (`JpaUserDetailsService`); `app_user` / `app_user_role` wired
- [x] Persistent JWK: RSA keys stored in `signing_key`; **key rotation** supported (all active keys
      published in the JWKS, current `kid` stamped on new tokens via `KeyIdJwtCustomizer`;
      `POST /api/admin/keys/rotate`)
- [x] Account lifecycle: registration (`POST /api/register`), password policy (min length +
      letter/digit; bcrypt delegating encoder), account lockout after N failed attempts
- [x] **MFA** (TOTP, RFC 6238) as a second factor — enroll/activate endpoints + login-form OTP field
- [x] Full audit log of auth events (login success/failure, lockout, MFA enrollment, key rotation,
      registration) — append-only `auth_audit_event` table

> Deferred to later phases (were parenthetical in the original Phase-2 bullets):
> admin API for CRUD-ing clients (beyond seeding), consent-event auditing, and moving the signing
> key's private material into a KMS/HSM (Phase 4 secrets). Argon2 can replace bcrypt by swapping the
> delegating encoder's default.

## Phase 3 — Fine-grained authorization (the differentiator) ✅ DONE
Goal: move from "authenticated?" to "allowed to do *this*?".

- [x] Integrate a policy engine — **OPA (Rego)** — as the Policy Decision Point (docker-compose
      `opa` service on :8181, loads `policies/`)
- [x] Gateway filter (`PolicyEnforcementFilter`, a reactive `GlobalFilter`) calls the PDP with
      (subject, action, resource, context) → allow/deny; **fail-closed** on any PDP error
- [x] RBAC + ABAC policies: roles (admin), scopes (`demo.read`/`demo.write`), resource ownership
      (`/api/users/{id}`), and time-of-day (writes only during business hours). Auth server now
      stamps a `roles` claim on user access tokens (`AegisJwtCustomizer`) so RBAC is meaningful
- [x] Policy-as-code: `policies/authz.rego` + `policies/authz_test.rego` versioned in-repo,
      runnable with `opa test policies/`

> Deferred / possible follow-ups: richer ABAC (tenant isolation, IP/CIDR allowlists — the input
> already carries `tenant` and `ip`), an OPA bundle server instead of a mounted volume, and
> decision logging shipped to the audit stream.

## Phase 4 — Production hardening & observability ✅ DONE
- [x] mTLS between gateway ↔ services (service identity, not just user identity): dev CA + signed
      client/server certs (`scripts/gen-dev-certs.*`), opt-in `mtls` profile on both sides
      (Boot SSL bundles; resource-demo `client-auth: need`, gateway presents its client cert)
- [x] OpenTelemetry tracing across all three services (Micrometer → OTLP → Jaeger); Prometheus +
      Grafana dashboards (docker-compose `jaeger`/`prometheus`/`grafana`, datasources provisioned)
- [x] Structured JSON logging with correlation/trace IDs, never logging tokens (Boot native ECS
      structured logging under the `prod` profile; traceId/spanId in the MDC via Micrometer Tracing)
- [x] Rate limiting per-client/per-user, not just per-route (`principalOrClientKeyResolver`);
      circuit breakers (Resilience4j `CircuitBreaker` filter + local 503 fallback)
- [x] Secrets via Vault (docker-compose `vault` in dev mode; `vault` profile on the auth server
      imports `vault://` via Spring Cloud Vault; datasource creds env-first everywhere;
      seed with `scripts/vault-seed.*`)

> Deferred / follow-ups: move the **JWT signing-key private material** (still PEM in Postgres,
> flagged since Phase 2) into Vault's transit engine or a KMS/HSM; production Vault auth
> (AppRole/K8s instead of dev token); mTLS for auth-server JWKS traffic and Redis/Postgres links.

## Phase 5 — DevSecOps & delivery (in progress)
- [x] GitHub Actions CI (`.github/workflows/ci.yml`): build + test (incl. Testcontainers IT on the
      Docker-enabled runner), OPA policy tests, **SAST** (Semgrep), **Trivy** filesystem scan
      (vuln + secret + misconfig → SARIF) and **container image scans** for all three services.
      CVE gating is report-only for now (flip Trivy `exit-code` to 1 to fail on CRITICAL/HIGH).
- [x] SBOM generation (CycloneDX) per module — Boot-native execution writes
      `META-INF/sbom/application.cdx.json` into each jar; uploaded as a CI artifact
- [x] Multi-stage Dockerfiles per service (Temurin 25 build → JRE runtime, non-root, healthcheck)
- [x] Helm chart (`deploy/helm/aegis`) — 3 Deployments + Services, ConfigMap/Secret, gateway
      Ingress, K8s liveness/readiness probes, hardened pod securityContext (helm lint + template OK)
- [ ] Deploy to a cloud free tier (needs a cluster + registry; chart is ready)
- [x] Security tests: token tampering, expired, `alg=none`, untrusted key, wrong issuer, malformed
      (`TokenSecurityTest`) + scope escalation (`authz_test.rego`)
- [x] Remaining security tests: rate-limit bypass (`RateLimitConfigTest`) + refresh-token replay
      (`RefreshTokenRotationIntegrationTest` — full auth-code+PKCE flow, rotation & replay-reject)

> Note: GitHub Actions Linux runners provide Docker, so CI runs the full `mvnw verify` including the
> `*IntegrationTest` classes (Testcontainers Postgres) — the ones skipped in local dev here.

## Phase 6 — Polish & storytelling (what actually lands interviews) (in progress)
- [x] Architecture diagram (Mermaid in `ARCHITECTURE.md`) + design rationale ("Key security decisions")
- [x] Threat model kept current; a "how I'd attack this" red-team narrative (`THREAT_MODEL.md`)
- [x] Demo script (`docs/DEMO.md`) — ~5-min end-to-end walkthrough
- [ ] Short recording + a live demo URL (needs the cloud deploy above)
- [x] README with badges (CI, SBOM, status) + clear quickstart

---

### Stretch ideas (optional, high signal)
- Device-code flow for CLI login · WebAuthn/passkeys · token exchange (RFC 8693) ·
  admin React dashboard · anomaly detection on the audit stream (ties into the SIEM idea).
