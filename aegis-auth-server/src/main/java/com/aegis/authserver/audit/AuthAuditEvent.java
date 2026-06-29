package com.aegis.authserver.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One append-only audit record of a security-relevant auth event. Rows are only ever
 * inserted — never updated or deleted by the application — so the table is a tamper-
 * evident trail of logins, lockouts, token issuance, MFA enrollment, and registration.
 * Detail text must never contain secrets or tokens.
 */
@Entity
@Table(name = "auth_audit_event")
public class AuthAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "principal", updatable = false)
    private String principal;

    @Column(name = "detail", updatable = false)
    private String detail;

    @Column(name = "remote_ip", updatable = false)
    private String remoteIp;

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
}
