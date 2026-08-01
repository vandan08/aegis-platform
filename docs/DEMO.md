# Aegis — Demo Script

Two scripts for two audiences.

- **[Script A — the console walkthrough](#script-a--the-console-walkthrough-3-minutes)**: browser
  only, no terminal. This is the one to record for a portfolio or a client, and the one to link.
- **[Script B — the terminal deep dive](#script-b--the-terminal-deep-dive-5-minutes)**: `curl`,
  traces, mTLS, SBOM. This is the one for an engineering interview.

---

# Script A — the console walkthrough (3 minutes)

Everything happens at the gateway's root URL (`https://app.yourdomain.com`, or
`http://localhost:8080` locally). See [DEPLOYMENT.md](DEPLOYMENT.md) to get a public URL.

**Why record this one:** the viewer watches a real policy engine refuse real requests, live, with
the reasoning on screen. No slides, no narration required to make the point land.

### Before you hit record
- Have the stack up and **already warm** — click through once so nothing cold-starts on camera.
- Sign out, so you start from the signed-out state.
- Zoom the browser to ~125%; the response bodies are small text.

### 0:00 — What this is (20s)
Open the console. Read the headline. One sentence: *"This is a zero-trust platform — an OAuth2
authorization server, a policy-enforcing gateway, and a service behind it. Everything you're about
to see is live."*

### 0:20 — Get a real identity (40s)
Click **Sign in with Aegis** → you land on the Aegis login page → sign in as the sandbox user.

Point out on the way back: *"That was a real Authorization Code flow with PKCE. The token now on
screen was signed a second ago."* Show the decoded token panel — the `RS256` algorithm, the `kid`
identifying which signing key, the scopes, and the **expiry counting down from five minutes**.

> The countdown is the best 3 seconds of the video. Short-lived credentials stop being a claim in a
> README and become a number ticking on screen.

### 1:00 — Allowed (20s)
Run **Read the demo API**. 200, `policy: allow`, and the response comes from the *downstream*
service — which validated the same token again itself.

### 1:20 — Refused, and why (60s)
This is the core. Run these three back to back:

| Scenario | What to say |
|---|---|
| **Read someone else's profile** | *"Same valid token, same endpoint, different owner — 403. And the service behind the gateway has no authorization code at all. The refusal happened at the edge."* |
| **No token at all** | *"Zero trust means being on the network buys you nothing."* |
| **Tampered signature** | *"One character of the signature flipped. It fails cryptographically — no revocation list, no lookup."* |

Expand the deny response and read the `evaluated` block aloud: subject, roles, scopes, action,
path, hour. *"It tells you exactly what it judged you on."*

### 2:20 — Attributes, not just roles (20s)
Run **Write, inside a time window**. Say the UTC hour shown on the page. Outside 09:00–17:00 UTC the
identical request is refused — *"the decision depends on an attribute of the request, not just on
who you are."*

### 2:40 — Abuse control (20s)
Run **Burst 25 requests**. 20 allowed, 5 rate-limited — *"and the bucket is keyed to my identity,
not my IP, so one noisy caller can't spend everyone else's budget."*

### 3:00 — The receipts (optional 30s)
Follow the **Tamper-evident audit log** link, sign in as admin, click **Verify chain integrity**.
*"Every security event is hash-chained. Editing one row breaks the chain, and the console will tell
you which row."*

### Closing line
*"Short-lived signed identity, verified at every hop, authorized by versioned policy that fails
closed — and you just watched it refuse me four different ways."*

---

# Script B — the terminal deep dive (5 minutes)

Commands assume the [README quickstart](../README.md) stack is up (`docker compose up -d` + the
three services running).

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
