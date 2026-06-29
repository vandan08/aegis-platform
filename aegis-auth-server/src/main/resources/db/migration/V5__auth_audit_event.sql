-- Phase 2: append-only audit trail of security-relevant auth events
-- (login success/failure, lockout, token issuance, MFA enrollment, registration).
-- Insert-only by convention; never updated or deleted in the application.

CREATE TABLE IF NOT EXISTS auth_audit_event (
    id          BIGSERIAL    PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    event_type  VARCHAR(64)  NOT NULL,   -- e.g. LOGIN_SUCCESS, LOGIN_FAILURE, ACCOUNT_LOCKED
    principal   VARCHAR(200),            -- username / client id, when known
    detail      VARCHAR(500),            -- human-readable context (never secrets/tokens)
    remote_ip   VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_audit_occurred_at ON auth_audit_event (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_principal  ON auth_audit_event (principal);
