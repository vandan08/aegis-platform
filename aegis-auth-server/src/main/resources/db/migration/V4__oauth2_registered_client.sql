-- Phase 2: persist OAuth2 clients instead of registering them in memory.
-- Column shape follows the canonical Spring Authorization Server client schema so the
-- JPA entity maps cleanly; settings that have no first-class column are stored as JSON.

CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id                            VARCHAR(100) PRIMARY KEY,
    client_id                     VARCHAR(100) NOT NULL,
    client_id_issued_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    client_secret                 VARCHAR(200),
    client_secret_expires_at      TIMESTAMPTZ,
    client_name                   VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000),
    post_logout_redirect_uris     VARCHAR(1000),
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               TEXT          NOT NULL,
    token_settings                TEXT          NOT NULL,
    CONSTRAINT uq_oauth2_client_id UNIQUE (client_id)
);
