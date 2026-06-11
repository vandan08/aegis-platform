# Aegis Platform

> A **Zero-Trust Identity & Access platform** — OAuth2/OIDC authorization server, a
> policy-enforcing API gateway, and a protected demo service. Built to production-minded standards
> to showcase modern, security-focused backend engineering.

[![Java](https://img.shields.io/badge/Java-25_LTS-red)]()
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.0-green)]()
[![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2025.1.2-green)]()
[![Status](https://img.shields.io/badge/status-Phase_3_complete-blue)]()

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

## Quickstart
```bash
# 1. Start infrastructure
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

## Documentation
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — components & request flow
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — the phased plan (Phase 1 → 6)
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — STRIDE analysis
- [`docs/DEVELOPMENT_LOG.md`](docs/DEVELOPMENT_LOG.md) — change journal
- [`CLAUDE.md`](CLAUDE.md) — full project context (also read automatically by Claude Code)

## Status
**Phase 3 complete — fine-grained authorization.** On top of Phase 2 (persistent identity, rotatable
signing keys, registration, lockout, TOTP MFA, audit log), the gateway now enforces externalized
OPA policies (RBAC + ABAC), failing closed. Not yet production-ready (secrets still in config, no
mTLS/observability/CI yet) — see the roadmap and threat model for what's deferred to Phases 4–6.
