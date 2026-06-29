package com.aegis.authserver.audit;

/**
 * Canonical set of audit event types. Kept as an enum so the values that land in the
 * append-only table are consistent and greppable.
 */
public enum AuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    ACCOUNT_LOCKED,
    TOKEN_ISSUED,
    USER_REGISTERED,
    MFA_ENROLLED,
    KEY_ROTATED
}
