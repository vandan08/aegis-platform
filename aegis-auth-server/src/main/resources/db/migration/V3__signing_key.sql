-- Phase 2: persistent, rotatable JWT signing keys.
-- Each row is one RSA key pair the auth server has used to sign tokens. The public
-- half of every ACTIVE key is published in the JWKS so tokens signed by any not-yet-
-- retired key still validate; new tokens are signed with the newest active key.
--
-- NOTE: private_key_pem is stored PEM here for a self-contained demo. In production it
-- MUST live in a KMS/HSM or be envelope-encrypted (see docs/ROADMAP.md Phase 4 — secrets).

CREATE TABLE IF NOT EXISTS signing_key (
    kid             VARCHAR(64)  PRIMARY KEY,           -- JWK key id
    public_key_pem  TEXT         NOT NULL,
    private_key_pem TEXT         NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,  -- published in JWKS while true
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Fast "newest active signing key" lookup.
CREATE INDEX IF NOT EXISTS idx_signing_key_active_created
    ON signing_key (active, created_at DESC);
