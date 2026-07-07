# Seeds the DEV Vault (docker compose `vault` service) with the auth server's secrets.
# The auth server reads these when started with SPRING_PROFILES_ACTIVE=vault.
# Uses Vault's KV v2 REST API directly — no vault CLI required.
$ErrorActionPreference = "Stop"

$vaultAddr = if ($env:VAULT_ADDR) { $env:VAULT_ADDR } else { "http://localhost:8200" }
$vaultToken = if ($env:VAULT_TOKEN) { $env:VAULT_TOKEN } else { "aegis-dev-token" }  # DEV ONLY

$body = @{
    data = @{
        "spring.datasource.username" = "aegis"
        "spring.datasource.password" = "aegis"   # DEV ONLY — rotate/replace in real envs
    }
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "$vaultAddr/v1/secret/data/aegis-auth-server" `
    -Headers @{ "X-Vault-Token" = $vaultToken } -ContentType "application/json" -Body $body | Out-Null

Write-Host "Seeded secret/aegis-auth-server in $vaultAddr."
Write-Host 'Start the auth server with: $env:SPRING_PROFILES_ACTIVE="vault"; ./mvnw -pl aegis-auth-server spring-boot:run'
