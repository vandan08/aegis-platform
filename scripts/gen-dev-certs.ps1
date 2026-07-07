# Generates the DEV-ONLY mTLS material for gateway <-> resource-demo service identity.
# Creates a local CA, a server cert for aegis-resource-demo (eku=serverAuth), and a
# client cert for aegis-gateway (eku=clientAuth), all signed by that CA.
#
# Output (gitignored, certs/):
#   ca.p12                       CA keypair (keep local; never commit)
#   ca-cert.pem                  CA certificate (what each side trusts)
#   resource-demo-keystore.p12   server identity presented by aegis-resource-demo
#   gateway-keystore.p12         client identity presented by aegis-gateway
#   truststore.p12               shared truststore containing only the dev CA
#
# Requires: keytool on PATH (any JDK 17+). Password is 'changeit' — DEV ONLY, see docs/ROADMAP.md.
$ErrorActionPreference = "Stop"

$certsDir = Join-Path $PSScriptRoot "..\certs"
New-Item -ItemType Directory -Force $certsDir | Out-Null
$storepass = "changeit"   # DEV ONLY

function KT { param([string[]]$KtArgs) & keytool @KtArgs; if ($LASTEXITCODE -ne 0) { throw "keytool failed: $($KtArgs -join ' ')" } }

Write-Host "==> 1/4 Dev CA"
KT @("-genkeypair","-alias","aegis-dev-ca","-keyalg","RSA","-keysize","3072","-validity","365",
     "-dname","CN=Aegis Dev CA,O=Aegis,OU=Dev",
     "-ext","bc:c","-ext","ku:c=digitalSignature,keyCertSign,cRLSign",
     "-keystore","$certsDir\ca.p12","-storetype","PKCS12","-storepass",$storepass)
KT @("-exportcert","-alias","aegis-dev-ca","-keystore","$certsDir\ca.p12","-storepass",$storepass,
     "-rfc","-file","$certsDir\ca-cert.pem")

function New-SignedIdentity {
    param([string]$Alias, [string]$Cn, [string]$Eku, [string]$Keystore)
    KT @("-genkeypair","-alias",$Alias,"-keyalg","RSA","-keysize","3072","-validity","365",
         "-dname","CN=$Cn,O=Aegis,OU=Dev",
         "-ext","san=dns:localhost,ip:127.0.0.1",
         "-keystore",$Keystore,"-storetype","PKCS12","-storepass",$storepass)
    KT @("-certreq","-alias",$Alias,"-keystore",$Keystore,"-storepass",$storepass,
         "-file","$certsDir\$Alias.csr")
    KT @("-gencert","-alias","aegis-dev-ca","-keystore","$certsDir\ca.p12","-storepass",$storepass,
         "-ext","san=dns:localhost,ip:127.0.0.1","-ext","ku:c=digitalSignature,keyEncipherment",
         "-ext","eku=$Eku","-validity","365","-rfc",
         "-infile","$certsDir\$Alias.csr","-outfile","$certsDir\$Alias-cert.pem")
    # Import the chain: CA first (so keytool can build it), then the signed leaf.
    KT @("-importcert","-alias","aegis-dev-ca","-keystore",$Keystore,"-storepass",$storepass,
         "-file","$certsDir\ca-cert.pem","-noprompt")
    KT @("-importcert","-alias",$Alias,"-keystore",$Keystore,"-storepass",$storepass,
         "-file","$certsDir\$Alias-cert.pem")
    Remove-Item "$certsDir\$Alias.csr","$certsDir\$Alias-cert.pem"
}

Write-Host "==> 2/4 resource-demo server identity"
New-SignedIdentity -Alias "resource-demo" -Cn "aegis-resource-demo" -Eku "serverAuth" `
                   -Keystore "$certsDir\resource-demo-keystore.p12"

Write-Host "==> 3/4 gateway client identity"
New-SignedIdentity -Alias "gateway" -Cn "aegis-gateway" -Eku "clientAuth" `
                   -Keystore "$certsDir\gateway-keystore.p12"

Write-Host "==> 4/4 shared truststore (CA only)"
KT @("-importcert","-alias","aegis-dev-ca","-keystore","$certsDir\truststore.p12",
     "-storetype","PKCS12","-storepass",$storepass,"-file","$certsDir\ca-cert.pem","-noprompt")

Write-Host ""
Write-Host "Done. Run both services with the 'mtls' profile, e.g.:"
Write-Host '  $env:SPRING_PROFILES_ACTIVE="mtls"; ./mvnw -pl aegis-resource-demo spring-boot:run'
Write-Host '  $env:SPRING_PROFILES_ACTIVE="mtls"; ./mvnw -pl aegis-gateway spring-boot:run'
