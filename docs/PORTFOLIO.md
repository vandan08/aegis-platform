# Aegis — Zero-Trust Identity & Access Platform

> Client-facing portfolio brief. Engineering detail lives in
> [ARCHITECTURE.md](ARCHITECTURE.md), [THREAT_MODEL.md](THREAT_MODEL.md) and [DEPLOYMENT.md](DEPLOYMENT.md).

---

## The problem

Most teams bolt authentication onto an app and call it security. The result: long-lived
tokens, authorization logic copy-pasted across services, no answer to "who did what, when?",
and an internal network that is implicitly trusted. One leaked credential and the whole
estate is open.

## What Aegis does

Aegis is a working **zero-trust** identity and access layer: every request is authenticated,
authorized against a central policy, rate-limited, traced, and recorded in an audit log that
**cannot be edited without detection**. No request is trusted because of where it came from.

---

## System at a glance

```
Browser / Client
      │  Authorization Code + PKCE
      ▼
┌──────────────────┐   short-lived signed JWT   ┌─────────────────┐
│  Auth Server     │ ─────────────────────────► │  API Gateway    │
│  OAuth2 / OIDC   │                            │  (Policy        │
│  MFA · lockout   │ ◄── JWKS (rotating keys) ──│   Enforcement)  │
│  hash-chained    │                            └───────┬─────────┘
│  audit log       │                          allow/deny│  mTLS
└──────────────────┘                                    ▼
        │                                    ┌─────────────────────┐
        │                                    │ Downstream service  │
   ┌────▼─────┐   ┌───────┐   ┌──────┐       │ re-validates JWT    │
   │ Postgres │   │ Redis │   │ OPA  │◄──────│ (defense in depth)  │
   └──────────┘   └───────┘   └──────┘       └─────────────────────┘
```

Three deployable services + Postgres, Redis and an Open Policy Agent decision point.

---

## Major parts

### 1. Authorization Server — the single source of identity
Full OAuth2 / OpenID Connect provider (Spring Authorization Server).

- Authorization Code **+ PKCE** and client-credentials flows.
- **Short-lived access tokens, rotating refresh tokens** — replaying a used refresh token is
  rejected (`invalid_grant`) and proven by an integration test.
- **Rotatable signing keys** held in Postgres, all active keys published in the JWKS so
  rotation never breaks live tokens; one admin endpoint rotates.
- **MFA (TOTP, RFC 6238)** with browser enrollment — QR code rendered server-side, two-step
  activate-before-trust rule.
- **Account lifecycle**: registration with a password policy, lockout after 5 failed
  attempts, self-service account page.

### 2. API Gateway — the Policy Enforcement Point
A reactive gateway that no request gets past unexamined.

- Validates every JWT at the edge; **fails closed** if the policy engine is unreachable.
- **Authorization as code**: RBAC, ABAC (resource ownership), scope checks and a
  time-of-day write window expressed in **Rego** and evaluated by **OPA** — policies are
  version-controlled and unit-tested, not buried in `if` statements.
- **Rate limiting** keyed per user/client (Redis), so the limit can't be dodged by varying
  the path or headers.
- **Circuit breaker** with a local fallback when a downstream service degrades.
- Denials return a JSON explanation naming the evaluated subject, roles, scopes and action —
  never the token, never the ruleset.

### 3. Tamper-evident audit log
Every security event (login, failure, lockout, MFA, key rotation, registration) is appended
to a **SHA-256 hash-chained** table: each row commits to the hash of the previous one.

- Appends lock the chain head, so the chain cannot fork under concurrency.
- An admin endpoint re-walks the chain and reports the **first broken row**.
- Verified against rewrite, mid-chain delete, reorder, injection and tail truncation.

### 4. Defense in depth downstream
The protected service **re-validates the JWT locally** instead of trusting the gateway, and
the gateway→service link can run over **mTLS** (opt-in profile, CA-signed client cert).

### 5. Observability
Distributed tracing (Micrometer → OpenTelemetry → OTLP/Jaeger) propagating edge to
downstream, Prometheus metrics, and structured ECS JSON logs carrying trace IDs — with
tokens and secrets never logged. Grafana dashboards included.

### 6. Security testing & CI/CD
The security posture is enforced by tests, not by claims:

- Forged-token suite: `alg=none`, untrusted key, tampered payload, expired, wrong issuer,
  malformed — all rejected with 401, plus a positive control.
- Rego policy tests (scope escalation blocked), rate-limit evasion tests, full auth-code +
  PKCE refresh-rotation integration test on a real Postgres (Testcontainers).
- **GitHub Actions**: build + test, OPA policy tests, **Semgrep** SAST, **Trivy** filesystem
  and image scans (SARIF), and a **CycloneDX SBOM** per service.

### 7. Ship it
Hardened multi-stage Docker images (non-root, healthchecks), a **Helm chart** (deployments,
services, ingress, probes, secrets, hardened securityContext), and two documented production
paths: single-VM Compose behind Caddy with automatic TLS, or a managed platform. Every URL,
port and credential is environment-driven.

### 8. Live interactive demo console
A browser console performs a **real** PKCE login and fires **real** requests through the
gateway, showing allow / deny / 401 / 429 alongside the exact policy input that produced the
decision. Clients can see zero-trust working rather than read about it.

---

## Engineering practices on display

| Area | What it shows |
|---|---|
| Threat modeling | STRIDE-style threat model + a written "how I would attack this" red-team pass |
| Documentation | Architecture, roadmap, deployment guide, demo script, append-only dev log |
| Schema discipline | Flyway-owned migrations, no auto-DDL, `open-in-view: false` |
| Test strategy | Unit, slice, integration (Testcontainers), policy and adversarial security tests |
| Config hygiene | No secrets in the repo; env-first config; Vault integration profile |

---

## Tech stack

**Java 25 · Spring Boot 4.1 · Spring Security 7 · Spring Authorization Server · Spring Cloud
Gateway (WebFlux) · Open Policy Agent (Rego) · PostgreSQL 18 · Redis 8 · Flyway ·
Testcontainers · Docker · Kubernetes/Helm · GitHub Actions · Semgrep · Trivy · OpenTelemetry
· Prometheus · Grafana · Jaeger · HashiCorp Vault · Maven**

---

## What a client gets from this engagement

1. An OAuth2/OIDC authorization server tailored to their identity model.
2. A gateway that enforces their authorization rules centrally, as testable code.
3. An audit trail they can hand to an auditor and defend.
4. Containers, Helm chart and a CI pipeline that fails the build on security regressions.
5. Architecture, threat model and runbook documentation their own team can maintain.
