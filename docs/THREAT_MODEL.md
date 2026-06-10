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
| **S**poofing | Attacker forges a token | RS256 signatures; services validate against JWKS; `alg=none` rejected by Spring Security |
| **T**ampering | Modify claims (e.g. elevate scope) | Signature covers all claims; any change invalidates it |
| **R**epudiation | User denies an action | Append-only audit log of auth events (Phase 2); trace IDs (Phase 4) |
| **I**nformation disclosure | Token/secret leakage in logs | Never log tokens/secrets; short TTLs; secrets from env/Vault (Phase 4); TLS everywhere (Phase 4) |
| **D**enial of service | Flood the gateway | Redis rate limiting (now); per-client limits + circuit breakers (Phase 4) |
| **E**levation of privilege | Use a low-priv token for admin action | Least-privilege scopes; fine-grained authz at the PDP (Phase 3); re-validate at each service |

## Known current weaknesses (Phase 1 scaffold — accepted, tracked)
- Signing key regenerated every boot and held in memory → **not** persistent/rotatable yet (Phase 2).
- Clients & users in memory; demo password in code → replace with JPA + secrets (Phase 2).
- No TLS locally; no mTLS between services yet (Phase 4).
- No MFA yet (Phase 2).

## Attacker's-eye checklist to test later (Phase 5 security tests)
- [ ] Expired token rejected (gateway + service)
- [ ] Tampered signature rejected
- [ ] `alg=none` / algorithm confusion rejected
- [ ] Token from a different issuer rejected
- [ ] Scope escalation blocked by the PDP
- [ ] Rate-limit cannot be bypassed by varying headers/paths
- [ ] Refresh-token replay detected (rotation)
