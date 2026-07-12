# Aegis — Threat Model (STRIDE)

Living document. A threat model in the repo is a strong signal to security-minded employers:
it shows you think like an attacker, not just a feature builder.

## Assets to protect
- User credentials & the token **signing private key** (crown jewels).
- Access/refresh tokens in transit and at rest.
- The audit log (integrity — must be tamper-evident).
- Authorization policies (Phase 3).

## STRIDE analysis

| Threat | Example | Mitigation (current / planned) |
|---|---|---|
| **S**poofing | Attacker forges a token | RS256 signatures; services validate against JWKS; `alg=none` rejected by Spring Security (proven in `TokenSecurityTest`); mTLS proves *workload* identity gateway↔service |
| **T**ampering | Modify claims (e.g. elevate scope); rewrite audit history via DB access | Signature covers all claims; any change invalidates it. Audit rows are **SHA-256 hash-chained** (Phase 6) — rewriting/deleting/reordering any row breaks every hash after it (`AuditChainVerifier`, `GET /api/admin/audit/verify`) |
| **R**epudiation | User denies an action; admin scrubs the trail | Append-only, **hash-chained** audit log of auth events (Phase 2 + 6); trace IDs (Phase 4) |
| **I**nformation disclosure | Token/secret leakage in logs | Never log tokens/secrets; short TTLs; secrets from env/Vault (Phase 4); TLS everywhere (Phase 4) |
| **D**enial of service | Flood the gateway | Redis rate limiting (now); per-client limits + circuit breakers (Phase 4) |
| **E**levation of privilege | Use a low-priv token for admin action | Least-privilege scopes; fine-grained authz at the PDP (Phase 3); re-validate at each service |

## Known current weaknesses (Phase 1 scaffold — accepted, tracked)
- Signing key regenerated every boot and held in memory → **not** persistent/rotatable yet (Phase 2).
- Clients & users in memory; demo password in code → replace with JPA + secrets (Phase 2).
- No TLS locally; no mTLS between services yet (Phase 4).
- No MFA yet (Phase 2).

## Attacker's-eye checklist (Phase 5 security tests)
Covered by `TokenSecurityTest` (resource-demo) and `authz_test.rego` (policies):
- [x] Expired token rejected → `TokenSecurityTest.rejectsExpiredToken`
- [x] Tampered payload (kept signature) rejected → `rejectsTokenWithTamperedPayload`
- [x] `alg=none` / signature stripping rejected → `rejectsAlgNoneToken`
- [x] Token signed by an untrusted key rejected → `rejectsTokenSignedByUntrustedKey`
- [x] Token from a different issuer rejected → `rejectsTokenFromWrongIssuer`
- [x] Malformed token → clean 401, not a 500 → `rejectsMalformedToken`
- [x] Scope escalation blocked by the PDP → `authz_test.test_read_scope_cannot_escalate_to_write`
- [ ] Rate-limit cannot be bypassed by varying headers/paths (limiter keys on identity, not path — add a test)
- [ ] Refresh-token replay detected (rotation) — behavioural test still to write

## "How I'd attack this" (red-team narrative)
Thinking like the adversary, and where each move dies:

1. **Steal a user's access token** (XSS, log scraping, MITM). Tokens live **5 minutes** and are
   never logged; TLS/mTLS protects them in transit. Even a fresh token only grants the scopes the
   policy allows — no ambient authority.
2. **Forge or downgrade a token** — `alg=none`, swap the signature, edit claims, or sign with my own
   key. All rejected: signatures are RS256 over the whole payload, validated against the auth
   server's JWKS at *both* the gateway and the service. Proven in `TokenSecurityTest`.
3. **Replay a captured refresh token.** Refresh tokens rotate (`reuseRefreshTokens(false)`); reusing
   a spent one invalidates the chain and returns `invalid_grant`
   (`RefreshTokenRotationIntegrationTest`).
4. **Skip the gateway and hit a service directly** (I'm inside the network). The service re-validates
   the JWT *and*, under mTLS, demands a CA-signed client cert only the gateway holds — the TLS
   handshake fails before my request is parsed. Network position grants nothing (the zero-trust win).
5. **Escalate privilege with a low-scope token** — call an admin/write path with `demo.read`. The OPA
   PDP denies it (403); scopes/roles/ownership/time-of-day are all checked centrally and fail closed
   if OPA is unreachable. Covered by `authz_test.rego`.
6. **Brute-force a password / drown the gateway.** Lockout after N failures (+ TOTP MFA) blunts
   credential stuffing; per-user/per-client rate limits (not per-route, so I can't evade by varying
   the path) and a circuit breaker keep one caller from starving the rest.
7. **Poison the supply chain / exploit a known CVE.** CI runs Semgrep (SAST), Trivy (deps, secrets,
   images), and publishes an SBOM per service, so a vulnerable dependency is visible and gated.
8. **Cover my tracks** — I got DB access, so I `UPDATE`/`DELETE` the audit rows that show my logins.
   Every audit event is SHA-256-linked to the one before it (certificate-transparency style), and a
   locked single-row head anchors the tail: rewriting, deleting, reordering, or injecting rows makes
   `GET /api/admin/audit/verify` name the first broken row (proven in `AuditChainTamperTest`).
   *Remaining move:* rewrite the head row **and** every row after my cut consistently — detectable
   only if the head hash is anchored externally, which is why the verify endpoint returns it
   (export it to the log pipeline / a second store; tracked as a follow-up).

**Where I'd keep pushing (known gaps):** the JWT signing key is still PEM-in-Postgres (a DB compromise
is game over until it moves to Vault transit/KMS); Vault itself runs dev-mode token auth; JWKS /
datastore links aren't mTLS yet; and the audit-chain head hash isn't externally anchored. These are
tracked below and in the roadmap.

## Supply-chain & pipeline (Phase 5)
- **SBOM** (CycloneDX) generated per module on every build and published as a CI artifact.
- **CI security gates** (`.github/workflows/ci.yml`): Semgrep SAST (Java/Spring/secrets/OWASP),
  Trivy filesystem scan (vuln + secret + misconfig, SARIF → Security tab), and Trivy image scans
  of all three service containers. CVE gating is report-only for now (flip Trivy `exit-code` to 1
  to fail on CRITICAL/HIGH once a baseline is triaged).
