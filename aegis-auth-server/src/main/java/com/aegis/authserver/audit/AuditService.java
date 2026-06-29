package com.aegis.authserver.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes append-only audit records. Each call opens its own transaction
 * ({@code REQUIRES_NEW}) so an audit write is never rolled back together with a
 * failing surrounding operation (e.g. a rejected login).
 */
@Service
public class AuditService {

    private final AuthAuditEventRepository repository;

    public AuditService(AuthAuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType type, String principal, String detail, String remoteIp) {
        repository.save(new AuthAuditEvent(type.name(), principal, detail, remoteIp));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType type, String principal, String detail) {
        record(type, principal, detail, null);
    }
}
