# Aegis Platform

> A **Zero-Trust Identity & Access platform** — OAuth2/OIDC authorization server, a
> policy-enforcing API gateway, and a protected demo service. Built to production-minded standards
> to showcase modern, security-focused backend engineering.

[![Java](https://img.shields.io/badge/Java-25_LTS-red)]()
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.0-green)]()
[![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2025.1.2-green)]()
[![CI](https://img.shields.io/badge/CI-GitHub_Actions-blue?logo=githubactions&logoColor=white)]()
[![SBOM](https://img.shields.io/badge/SBOM-CycloneDX-black)]()
[![Status](https://img.shields.io/badge/status-Phase_6_in_progress-blue)]()

## Why this project
Zero-trust IAM is one of the highest-signal domains in backend security (think Okta, Auth0,
Cloudflare, HashiCorp). This project demonstrates OAuth2/OIDC, JWT-based auth, an API gateway as a
policy-enforcement point, defense-in-depth, and a real path to production (persistence, MFA,
policy-as-code, observability, DevSecOps). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Modules
| Module | Port | Description |
|---|---|---|
| `aegis-auth-server` | 9000 | OAuth2 / OIDC authorization server (issues signed JWTs) |
| `aegis-gateway` | 8080 | Reactive, policy-enforcing API gateway (validates every request) |
| `aegis-resource-demo` | 8081 | Protected downstream service; re-validates the JWT |

## Tech stack
Java 25 (LTS) · Spring Boot 4.1.0 · Spring Cloud 2025.1.2 · Spring Authorization Server ·
Spring Security 7 · PostgreSQL 18 · Redis 8 · **OPA (Rego)** · Flyway · Testcontainers ·
Maven (multi-module).

## Fine-grained authorization (Phase 3)
The gateway is a Policy **Enforcement** Point: after validating the JWT it asks **OPA** (the Policy
**Decision** Point, `policies/authz.rego`) whether this `(subject, action, resource, context)` is
allowed, and returns **403** if not. It **fails closed** if OPA is unreachable. Policies cover RBAC
(admin role, `demo.read`/`demo.write` scopes) and ABAC (resource ownership, business-hours writes),
and are versioned in-repo with tests:
```bash
opa test policies/          # policy unit tests
```

## Observability & resilience (Phase 4)
Every service exports **traces** (Micrometer → OpenTelemetry → OTLP) and **Prometheus metrics**, and
under the `prod` profile logs **ECS JSON** to stdout with `traceId`/`spanId` on every line (never
tokens). The gateway rate-limits **per user / per client** (not per route) and wraps the downstream
route in a **Resilience4j circuit breaker** with a local 503 fallback. `docker compose up -d` brings
up the full stack:

| Tool | URL | Purpose |
|---|---|---|
| Jaeger | http://localhost:16686 | Distributed traces (end-to-end across all three services) |
| Prometheus | http://localhost:9090 | Scrapes `/actuator/prometheus` on each service |
| Grafana | http://localhost:3000 | Dashboards (Prometheus + Jaeger datasources auto-provisioned) |

Run services with the `prod` profile to see JSON logs: `SPRING_PROFILES_ACTIVE=prod ./mvnw -pl aegis-gateway spring-boot:run`.

### Service identity: mTLS (opt-in `mtls` profile)
A user JWT proves *who is calling*; mTLS proves *which workload is calling*. With the `mtls`
profile, the resource service serves HTTPS and **requires** a client certificate signed by the dev
CA — only the gateway holds one, so bypassing the gateway fails at the TLS handshake before any
request is processed:
```bash
scripts/gen-dev-certs.sh        # one-time: dev CA + signed client/server certs -> certs/ (gitignored)
SPRING_PROFILES_ACTIVE=mtls ./mvnw -pl aegis-resource-demo spring-boot:run
SPRING_PROFILES_ACTIVE=mtls ./mvnw -pl aegis-gateway spring-boot:run
```

### Secrets: HashiCorp Vault (opt-in `vault` profile)
Credentials are env-first (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`) and, with the `vault` profile,
pulled from Vault's KV store instead of any file:
```bash
docker compose up -d vault
scripts/vault-seed.sh           # writes secret/aegis-auth-server (dev values)
SPRING_PROFILES_ACTIVE=vault ./mvnw -pl aegis-auth-server spring-boot:run
```

## Quickstart
```bash
# 1. Start infrastructure (Postgres, Redis, OPA, Jaeger, Prometheus, Grafana)
docker compose up -d

# 2. Run each service (separate terminals)
./mvnw -pl aegis-auth-server   spring-boot:run
./mvnw -pl aegis-gateway       spring-boot:run
./mvnw -pl aegis-resource-demo spring-boot:run
```

**Smoke test (machine-to-machine, no browser).** Obtain a token via the confidential
`aegis-service-client` and call the gateway:
```bash
TOKEN=$(curl -s -u aegis-service-client:service-secret \
  -d grant_type=client_credentials -d scope=demo.read \
  http://localhost:9000/oauth2/token | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/demo/whoami
```
For an interactive login, use the Authorization Code + PKCE client `aegis-web-client` and the demo
user `admin` / `changeit` (all DEV-only credentials).

### Phase-2 endpoints (auth server, :9000)
| Method & path | Auth | Purpose |
|---|---|---|
| `POST /api/register` | public | Self-service registration (password policy enforced) |
| `POST /api/mfa/enroll` | session | Generate a TOTP secret + `otpauth://` URI |
| `POST /api/mfa/activate` | session | Confirm a code to switch MFA on |
| `POST /api/admin/keys/rotate` | ROLE_ADMIN | Rotate the JWT signing key |

## Build & test
```bash
./mvnw verify        # unit + integration tests (auth-server IT needs Docker for Testcontainers)
```
Each `package` also emits a **CycloneDX SBOM** per module, bundled into the jar at
`META-INF/sbom/application.cdx.json` and served at the `sbom` actuator endpoint.

## CI/CD & security scanning (Phase 5)
[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push/PR to `main`:
- **Build & test** on JDK 25 — full `mvnw verify`, including the Testcontainers integration test
  (GitHub's Linux runners provide Docker), with SBOMs uploaded as artifacts.
- **OPA policy tests** — `opa test policies/`.
- **SAST** — Semgrep (`p/java`, `p/secrets`, `p/owasp-top-ten`).
- **Trivy** — filesystem scan (vulns + secrets + misconfig, results in the Security tab) and image
  scans of all three service containers.

Container images build from per-service **multi-stage Dockerfiles** (Temurin 25 build → JRE
runtime, non-root user, healthcheck):
```bash
docker build -f aegis-gateway/Dockerfile -t aegis/gateway .
```

### Kubernetes (Helm)
A chart in [`deploy/helm/aegis`](deploy/helm/aegis) deploys the three services (Deployments +
ClusterIP Services, a ConfigMap/Secret, and an Ingress exposing only the gateway), with K8s
liveness/readiness probes and a hardened pod security context. It assumes Postgres/Redis/OPA exist
in-cluster (point `values.postgres/redis/opa` at them).
```bash
helm lint deploy/helm/aegis
helm install aegis deploy/helm/aegis \
  --set image.registry=ghcr.io/you --set secrets.existingSecret=aegis-db   # supply secrets out-of-band
```

### Security tests
`TokenSecurityTest` (resource-demo) proves the service rejects the classic JWT attacks — `alg=none`,
untrusted-key signatures, payload tampering, expired tokens, wrong issuer, and malformed input — each
with a clean **401**. Scope escalation is blocked at the policy layer (`authz_test.rego`), rate-limit
buckets can't be evaded by varying the path (`RateLimitConfigTest`), and refresh tokens rotate with
replay rejected (`RefreshTokenRotationIntegrationTest`).

## Documentation
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — components, system diagram & request flow
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — the phased plan (Phase 1 → 6)
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — STRIDE analysis + "how I'd attack this"
- [`docs/DEMO.md`](docs/DEMO.md) — ~5-minute end-to-end demo script
- [`docs/DEVELOPMENT_LOG.md`](docs/DEVELOPMENT_LOG.md) — change journal
- [`CLAUDE.md`](CLAUDE.md) — full project context (also read automatically by Claude Code)

## Status
**Phases 1–5 done; Phase 6 (polish) in progress.** The platform has persistent identity with
rotating keys, MFA, lockout and audit (P2); externalized OPA authorization, RBAC + ABAC, fail-closed
(P3); tracing, metrics, JSON logs, per-caller rate limiting, circuit breaking, mTLS service identity,
and Vault secrets (P4); plus a full DevSecOps pipeline — CI with Semgrep/Trivy scans, SBOMs,
multi-stage Dockerfiles, a Helm chart, and a security-test suite proving the JWT attacks bounce (P5).
Remaining: a live cloud deploy + recording, and the hardening follow-ups (signing key → Vault
transit, AppRole auth) tracked in the roadmap.
