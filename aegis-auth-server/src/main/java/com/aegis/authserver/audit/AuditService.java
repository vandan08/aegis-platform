package com.aegis.authserver.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes append-only, hash-chained audit records. Each call opens its own transaction
 * ({@code REQUIRES_NEW}) so an audit write is never rolled back together with a
 * failing surrounding operation (e.g. a rejected login).
 *
 * <p>Within that transaction the {@link AuditChainHead} row is loaded under a
 * pessimistic write lock, the new event is hashed against the head's last hash
 * ({@link AuditHashChain}), and the head advances to the new tip. The lock serializes
 * concurrent appends — a deliberate trade of a little write throughput (auth events
 * are low-volume) for a chain that can never fork.
 */
@Service
public class AuditService {

    private final AuthAuditEventRepository repository;
    private final AuditChainHeadRepository chainHeadRepository;

    public AuditService(AuthAuditEventRepository repository,
            AuditChainHeadRepository chainHeadRepository) {
        this.repository = repository;
        this.chainHeadRepository = chainHeadRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType type, String principal, String detail, String remoteIp) {
        // Seeded at genesis by migration V6; orElseGet is defensive only.
        AuditChainHead head = chainHeadRepository.lockHead().orElseGet(AuditChainHead::genesis);
        AuthAuditEvent event = new AuthAuditEvent(type.name(), principal, detail, remoteIp);
        String entryHash = AuditHashChain.entryHash(head.getLastHash(), event);
        event.seal(head.getLastHash(), entryHash);
        event = repository.save(event);
        head.advanceTo(event.getId(), entryHash);
        chainHeadRepository.save(head);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType type, String principal, String detail) {
        record(type, principal, detail, null);
    }
}
