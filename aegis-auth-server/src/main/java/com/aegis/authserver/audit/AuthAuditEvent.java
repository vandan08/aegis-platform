package com.aegis.authserver.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One append-only audit record of a security-relevant auth event (logins, lockouts,
 * token issuance, MFA enrollment, registration). Detail text must never contain
 * secrets or tokens.
 *
 * <p>Tamper evidence is cryptographic, not just convention: {@code entryHash} is a
 * SHA-256 over this event's content and {@code prevHash} (the previous event's hash),
 * computed by {@link AuditHashChain} — see {@link AuditService} for how the chain is
 * appended and {@link AuditChainVerifier} for how it is checked. Rows written before
 * migration V6 predate the chain and have {@code null} hashes.
 */
@Entity
@Table(name = "auth_audit_event")
public class AuthAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    // Truncated to microseconds so the value survives the Postgres TIMESTAMPTZ
    // round-trip unchanged — chain re-verification hashes the stored timestamp.
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "principal", updatable = false)
    private String principal;

    @Column(name = "detail", updatable = false)
    private String detail;

    @Column(name = "remote_ip", updatable = false)
    private String remoteIp;

    @Column(name = "prev_hash", updatable = false, length = 64)
    private String prevHash;

    @Column(name = "entry_hash", updatable = false, length = 64)
    private String entryHash;

    protected AuthAuditEvent() {
        // for JPA
    }

    public AuthAuditEvent(String eventType, String principal, String detail, String remoteIp) {
        this.eventType = eventType;
        this.principal = principal;
        this.detail = detail;
        this.remoteIp = remoteIp;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getDetail() {
        return detail;
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    /** Links this event into the hash chain; called once by {@link AuditService} before insert. */
    void seal(String prevHash, String entryHash) {
        this.prevHash = prevHash;
        this.entryHash = entryHash;
    }
}
