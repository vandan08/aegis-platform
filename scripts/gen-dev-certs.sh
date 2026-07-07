#!/usr/bin/env bash
# Generates the DEV-ONLY mTLS material for gateway <-> resource-demo service identity.
# See gen-dev-certs.ps1 for the annotated version; both scripts produce identical output.
# Requires: keytool on PATH (any JDK 17+). Password 'changeit' — DEV ONLY (docs/ROADMAP.md).
set -euo pipefail

CERTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/certs"
mkdir -p "$CERTS_DIR"
STOREPASS=changeit   # DEV ONLY

echo "==> 1/4 Dev CA"
keytool -genkeypair -alias aegis-dev-ca -keyalg RSA -keysize 3072 -validity 365 \
  -dname "CN=Aegis Dev CA,O=Aegis,OU=Dev" \
  -ext bc:c -ext ku:c=digitalSignature,keyCertSign,cRLSign \
  -keystore "$CERTS_DIR/ca.p12" -storetype PKCS12 -storepass "$STOREPASS"
keytool -exportcert -alias aegis-dev-ca -keystore "$CERTS_DIR/ca.p12" -storepass "$STOREPASS" \
  -rfc -file "$CERTS_DIR/ca-cert.pem"

signed_identity() { # alias cn eku keystore
  local alias="$1" cn="$2" eku="$3" keystore="$4"
  keytool -genkeypair -alias "$alias" -keyalg RSA -keysize 3072 -validity 365 \
    -dname "CN=$cn,O=Aegis,OU=Dev" -ext "san=dns:localhost,ip:127.0.0.1" \
    -keystore "$keystore" -storetype PKCS12 -storepass "$STOREPASS"
  keytool -certreq -alias "$alias" -keystore "$keystore" -storepass "$STOREPASS" \
    -file "$CERTS_DIR/$alias.csr"
  keytool -gencert -alias aegis-dev-ca -keystore "$CERTS_DIR/ca.p12" -storepass "$STOREPASS" \
    -ext "san=dns:localhost,ip:127.0.0.1" -ext "ku:c=digitalSignature,keyEncipherment" \
    -ext "eku=$eku" -validity 365 -rfc \
    -infile "$CERTS_DIR/$alias.csr" -outfile "$CERTS_DIR/$alias-cert.pem"
  keytool -importcert -alias aegis-dev-ca -keystore "$keystore" -storepass "$STOREPASS" \
    -file "$CERTS_DIR/ca-cert.pem" -noprompt
  keytool -importcert -alias "$alias" -keystore "$keystore" -storepass "$STOREPASS" \
    -file "$CERTS_DIR/$alias-cert.pem"
  rm -f "$CERTS_DIR/$alias.csr" "$CERTS_DIR/$alias-cert.pem"
}

echo "==> 2/4 resource-demo server identity"
signed_identity resource-demo aegis-resource-demo serverAuth "$CERTS_DIR/resource-demo-keystore.p12"

echo "==> 3/4 gateway client identity"
signed_identity gateway aegis-gateway clientAuth "$CERTS_DIR/gateway-keystore.p12"

echo "==> 4/4 shared truststore (CA only)"
keytool -importcert -alias aegis-dev-ca -keystore "$CERTS_DIR/truststore.p12" \
  -storetype PKCS12 -storepass "$STOREPASS" -file "$CERTS_DIR/ca-cert.pem" -noprompt

echo
echo "Done. Run both services with the 'mtls' profile:"
echo "  SPRING_PROFILES_ACTIVE=mtls ./mvnw -pl aegis-resource-demo spring-boot:run"
echo "  SPRING_PROFILES_ACTIVE=mtls ./mvnw -pl aegis-gateway spring-boot:run"
