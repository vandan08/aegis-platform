# Aegis — Demo Script

A ~5-minute walkthrough that shows the zero-trust story end to end. Works as a live demo or a
recording outline. Commands assume the [README quickstart](../README.md) stack is up
(`docker compose up -d` + the three services running).

> Tip for recording: keep three panes — the running services (JSON logs), a shell for `curl`, and
> browser tabs for Grafana/Jaeger. Narrate the *why* at each step, not just the *what*.

---

## 0. Setup (before you hit record)
```bash
docker compose up -d                                  # postgres, redis, opa, vault, jaeger, prometheus, grafana
SPRING_PROFILES_ACTIVE=prod ./mvnw -pl aegis-auth-server   spring-boot:run
SPRING_PROFILES_ACTIVE=prod ./mvnw -pl aegis-gateway       spring-boot:run
SPRING_PROFILES_ACTIVE=prod ./mvnw -pl aegis-resource-demo spring-boot:run
```
Open: Grafana http://localhost:3000 · Jaeger http://localhost:16686 · auth-server login http://localhost:9000/login

---

## 1. Identity — get a token (30s)
"Every request needs a short-lived, signed identity. Here's a machine getting one."
```bash
TOKEN=$(curl -s -u aegis-service-client:service-secret \
  -d grant_type=client_credentials -d scope=demo.read \
  http://localhost:9000/oauth2/token | jq -r .access_token)
echo "$TOKEN" | cut -c1-40      # show it's a JWT, don't read the whole thing aloud
```
Paste the JWT into jwt.io (offline) to show the `roles`/`scope` claims and the `kid`.

## 2. Zero-trust enforcement — the happy path (30s)
"The gateway authenticates, authorizes via policy, then forwards."
```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/demo/whoami | jq
```
Returns the identity claims echoed by the downstream service (which re-validated the token itself).

## 3. Attacks that bounce (90s) — the core of the pitch
```bash
# No token -> 401 at the edge
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/demo/whoami          # 401

# Tampered token -> 401 (signature covers the claims)
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer ${TOKEN}x" http://localhost:8080/api/demo/whoami            # 401

# Valid token, but a write with only demo.read -> 403 from the OPA policy engine
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/demo/thing        # 403
```
"Authentication is *who are you*; authorization is *are you allowed to do this* — decided by an
external, versioned policy that fails closed."

## 4. Defense in depth — bypass the gateway (30s)
"Say I'm inside the network and skip the gateway." (Run both services under the `mtls` profile.)
```bash
curl -sk https://localhost:8081/api/demo/whoami                                          # TLS handshake fails
```
"The service demands a client certificate only the gateway holds. Network position buys nothing."

## 5. Observability — one request, one trace (60s)
- **Jaeger**: search the `aegis-gateway` service → open a trace → show the span crossing
  gateway → resource-demo (one trace ID, two services).
- **Grafana**: open the Prometheus datasource → show request-rate / latency per `application`.
- **Logs**: point at the JSON console lines — each carries the same `traceId`/`spanId`. No tokens.

## 6. Supply chain (20s)
```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/actuator/sbom     # (or view target/.../application.cdx.json)
```
"Every build ships a CycloneDX SBOM, and CI scans deps, code, and images on every push."

---

## Closing line
"Short-lived signed identity, verified at every hop, authorized by policy, observable end to end,
and shipped through a scanning pipeline — that's zero trust, not as a slogan but as running code."
