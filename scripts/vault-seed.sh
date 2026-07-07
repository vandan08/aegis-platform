#!/usr/bin/env bash
# Seeds the DEV Vault (docker compose `vault` service) with the auth server's secrets.
# The auth server reads these when started with SPRING_PROFILES_ACTIVE=vault.
# Uses Vault's KV v2 REST API directly — no vault CLI required.
set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-aegis-dev-token}"   # DEV ONLY

curl -sf -X POST "$VAULT_ADDR/v1/secret/data/aegis-auth-server" \
  -H "X-Vault-Token: $VAULT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"data":{"spring.datasource.username":"aegis","spring.datasource.password":"aegis"}}' > /dev/null

echo "Seeded secret/aegis-auth-server in $VAULT_ADDR."
echo "Start the auth server with: SPRING_PROFILES_ACTIVE=vault ./mvnw -pl aegis-auth-server spring-boot:run"
