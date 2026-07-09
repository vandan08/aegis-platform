-- Phase 6: make the audit trail *provably* tamper-evident, not just append-only by
-- convention. Every new event carries a SHA-256 hash of its own content linked to the
-- hash of the previous event (a hash chain, as in certificate-transparency logs).
-- Rewriting, deleting, or reordering any chained row breaks every hash after it.
--
-- audit_chain_head is a single-row anchor holding the current chain tip; appends lock
-- it (SELECT ... FOR UPDATE) so concurrent writers cannot fork the chain. Rows written
-- before this migration have NULL hashes and are reported as "legacy" by verification.

ALTER TABLE auth_audit_event ADD COLUMN prev_hash  VARCHAR(64);
ALTER TABLE auth_audit_event ADD COLUMN entry_hash VARCHAR(64);

CREATE TABLE audit_chain_head (
    id            SMALLINT    PRIMARY KEY CHECK (id = 1),  -- exactly one row, ever
    last_event_id BIGINT,                                  -- id of the newest chained event
    last_hash     VARCHAR(64) NOT NULL                     -- its entry_hash (genesis: 64 zeros)
);

INSERT INTO audit_chain_head (id, last_event_id, last_hash)
VALUES (1, NULL, repeat('0', 64));
