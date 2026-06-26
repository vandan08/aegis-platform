-- Phase 2: extend app_user for the real account lifecycle.
-- Adds TOTP MFA secret storage and account-lockout bookkeeping.
-- (mfa_enabled already exists from V1.)

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS mfa_secret            VARCHAR(64);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS lockout_until         TIMESTAMPTZ;
