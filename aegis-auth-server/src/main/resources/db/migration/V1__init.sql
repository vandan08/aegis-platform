-- Aegis Auth Server — initial schema.
-- Phase 1 uses in-memory users/clients; this table is the forward-looking home
-- for real accounts (wired up in Phase 2). See docs/ROADMAP.md.

CREATE TABLE IF NOT EXISTS app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    mfa_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS app_user_role (
    user_id UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role    VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role)
);
