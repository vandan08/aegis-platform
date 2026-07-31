# Deploying Aegis

Aegis is not a single web app, so it does not deploy like one. It is **four containers plus
two data stores**, and they have to be able to find each other:

| Component | Public? | Why |
|---|---|---|
| `aegis-auth-server` | **yes** | users sign in here; its URL is baked into every token's `iss` claim |
| `aegis-gateway` | **yes** | the only entry point for the APIs; also serves the demo console |
| `aegis-resource-demo` | no | reached only through the gateway |
| OPA (policy engine) | no | the gateway asks it for every authorization decision |
| PostgreSQL | no | users, clients, signing keys, audit chain |
| Redis | no | rate-limiter token buckets |

**Vercel cannot host this.** Vercel runs serverless functions and static sites; these are
long-lived JVM processes that hold database pools, a Redis connection, and in-memory circuit-breaker
state. Two paths below actually work. Read the comparison, pick one, and ignore the other.

---

## Which path?

| | **A — One VM + Docker Compose** | **B — Render Blueprint** |
|---|---|---|
| New concepts | SSH, DNS, `docker compose` | a dashboard and a YAML file |
| Cost | ~$5–6/mo total (one small VM) | free tier, or ~$28/mo for 4 paid services |
| Cold starts | none — always warm | free tier sleeps after 15 min; **a JVM takes 50–90s to wake** |
| HTTPS | automatic (Caddy + Let's Encrypt) | automatic |
| Custom domain | yes | yes |
| Best for | **a portfolio link a client will click** | preview environments, CI |

**Recommendation: path A.** For an Upwork profile the deciding factor is the cold start. A
prospective client clicks your link once; on Render's free tier they wait a minute and a half
staring at a blank page, and four sleeping services means the auth server, the gateway, the policy
engine and the downstream all wake in sequence. One always-on VM costs less than one paid Render
service and hosts the entire platform.

---

## Path A — one VM with Docker Compose (recommended)

### What you need

- A VM with 2 GB+ RAM. Hetzner CX22 (~€4/mo) or DigitalOcean's $6 droplet are both fine.
  4 GB is more comfortable — six containers, three of them JVMs.
- A domain name, with the ability to add DNS records.

### 1. Point two subdomains at the server

Create two **A records**, both pointing at the VM's public IPv4 address:

```
app.yourdomain.com   ->  203.0.113.10
auth.yourdomain.com  ->  203.0.113.10
```

Do this **first**. Caddy proves it controls these names to obtain certificates, and that check
runs when the stack starts. Verify before continuing:

```bash
dig +short app.yourdomain.com auth.yourdomain.com
```

### 2. Install Docker on the VM

SSH in, then:

```bash
curl -fsSL https://get.docker.com | sh
```

### 3. Get the code and configure it

```bash
git clone https://github.com/<you>/aegis-platform.git && cd aegis-platform
```

```bash
cp deploy/.env.example deploy/.env
```

Edit `deploy/.env` and set the two hostnames, your email, and a **generated** database password:

```bash
openssl rand -base64 24
```

### 4. Start it

```bash
docker compose -f deploy/compose.prod.yml --env-file deploy/.env up -d --build
```

The first build compiles three Spring Boot applications inside Docker and takes 5–15 minutes.
Later deploys reuse the cached dependency layers and are much faster.

Watch it come up:

```bash
docker compose -f deploy/compose.prod.yml --env-file deploy/.env logs -f
```

### 5. Verify

```bash
curl -s https://auth.yourdomain.com/.well-known/openid-configuration | head -c 300
```

The `issuer` in that response must be exactly `https://auth.yourdomain.com`. If it says
`http://` or shows an internal hostname, `AEGIS_ISSUER_URI` is wrong — fix it and restart, because
every token minted with a wrong issuer will be rejected by the gateway.

Then open `https://app.yourdomain.com` and run the console's scenarios.

### 6. Lock down the demo admin

The seeded `admin` / `changeit` account is documented in this repo and therefore public. Before
you share the link, sign in as `admin` and change it, or disable the account. The `alice` sandbox
account is *meant* to be public — that one is fine.

### Updating after a code change

```bash
git pull && docker compose -f deploy/compose.prod.yml --env-file deploy/.env up -d --build
```

---

## Path B — Render Blueprint

[`render.yaml`](../render.yaml) at the repo root describes every service. Render reads it and
creates all of them at once.

### Steps

1. Push this repository to GitHub.
2. In Render: **New → Blueprint**, and select the repo.
3. Render parses `render.yaml` and prompts for the values marked `sync: false`. On the first pass
   the service URLs do not exist yet, so enter placeholders and correct them in step 5.
4. Let the first deploy finish. Note the two generated URLs, e.g.
   `https://aegis-auth-server.onrender.com` and `https://aegis-gateway.onrender.com`.
5. Set the real values in each service's **Environment** tab, then redeploy:

   | Service | Variable | Value |
   |---|---|---|
   | all three apps | `AEGIS_ISSUER_URI` | the auth server's URL |
   | auth server | `AEGIS_CORS_ALLOWED_ORIGINS` | the gateway's URL |
   | auth server | `AEGIS_WEB_CLIENT_REDIRECT_URIS` | the gateway's URL **with a trailing slash** |
   | auth server + gateway | `AEGIS_DEMO_PASSWORD` | your sandbox password |

The trailing slash matters. The console redirects to exactly `https://<gateway>/`, and the
authorization server compares redirect URIs literally — no slash means `invalid_redirect_uri`
and the login never starts.

### Known rough edges

- **Free services sleep.** First request after idle takes 50–90s per service.
- **The free Postgres instance expires** (currently 30 days), taking your users, audit chain and
  signing keys with it. Fine for a demo, not for a link you leave up for months.
- **`type: keyvalue`** is Render's current name for its Redis product. Older blueprints used
  `type: redis`. If Render rejects that key, it is the one line to change.
- **The downstream service runs as a public web service**, because Render's free tier has no
  private services. On a paid plan change it to `type: pserv`. It is not actually exposed in any
  meaningful sense — reached directly without a token it returns 401, because it validates the JWT
  itself rather than trusting the gateway.

---

## Configuration reference

Every deployment is configured entirely through environment variables. No file needs editing.

| Variable | Applies to | Meaning |
|---|---|---|
| `AEGIS_ISSUER_URI` | all three | public URL of the auth server; the `iss` claim and JWKS source |
| `AEGIS_CORS_ALLOWED_ORIGINS` | auth server | origins allowed to POST to `/oauth2/token` |
| `AEGIS_WEB_CLIENT_REDIRECT_URIS` | auth server | comma-separated; must match the console URL exactly |
| `AEGIS_DEMO_USER` / `AEGIS_DEMO_PASSWORD` | auth server, gateway | the sandbox account, shown on the console |
| `AEGIS_RESOURCE_DEMO_URI` | gateway | where to proxy `/api/**` |
| `AEGIS_OPA_URL` | gateway | the policy decision point |
| `REDIS_URL` | gateway | `redis://` or `rediss://`, credentials included |
| `DB_URL` *or* `DB_HOST`/`DB_PORT`/`DB_NAME` | auth server | whole JDBC URL, or the parts to build one from |
| `DB_USERNAME` / `DB_PASSWORD` | auth server | database credentials |
| `PORT` | all | the port to bind; injected automatically by most platforms |
| `SPRING_PROFILES_ACTIVE` | all | use `cloud,prod` |

### What the `cloud` profile changes

- **Tracing export off.** There is no OTLP collector in these deployments, and leaving it on means
  every span retries against a dead endpoint. Metrics and health stay on.
- **Forwarded headers honoured** (`server.forward-headers-strategy: framework`). Behind a TLS
  terminator the app itself speaks plain HTTP; without this, Spring builds redirects and the OIDC
  discovery document as `http://internal-host:port` and the login flow breaks in a way that looks
  like a certificate problem but is not.

`prod` adds ECS-format JSON logging on stdout with trace IDs. Use both: `cloud,prod`.

---

## Troubleshooting

**Login redirects to an `invalid_redirect_uri` error.** `AEGIS_WEB_CLIENT_REDIRECT_URIS` does not
exactly match the console's URL. Check the scheme (`https`, not `http`) and the trailing slash. The
registration is reconciled on every boot, so correcting the variable and restarting is enough.

**Every API call returns 401 with a valid-looking token.** The `iss` claim does not match what the
gateway expects. Both must have the same `AEGIS_ISSUER_URI`. Decode the token at
`https://<auth>/oauth2/jwks` and compare.

**Every API call returns 403.** The gateway cannot reach OPA, and the PDP fails **closed** by
design — an unreachable policy engine denies rather than admits. Check `AEGIS_OPA_URL` and that the
OPA container is running.

**The console loads but sign-in does nothing.** Open the browser console. A CORS error on
`/oauth2/token` means `AEGIS_CORS_ALLOWED_ORIGINS` is missing the gateway's origin.

**`Schema validation: missing table [app_user]`.** Flyway did not run. The database is reachable but
empty; check the auth server's logs for Flyway migration lines.

**Postgres rejects the connection with a timezone error.** Set `TZ=UTC` on the auth server.
Postgres 18 rejects the legacy `Asia/Calcutta` alias that some JVMs report.
