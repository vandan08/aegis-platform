package com.aegis.authserver.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The single-row anchor of the audit hash chain: the id and hash of the newest chained
 * event. Appends take a pessimistic write lock on this row so concurrent audit writes
 * are serialized and cannot fork the chain. It also acts as the tamper-evidence anchor
 * for the chain <em>tail</em>: deleting the newest events leaves this row pointing at a
 * hash the remaining rows can no longer reproduce.
 */
@Entity
@Table(name = "audit_chain_head")
public class AuditChainHead {

    static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private short id = SINGLETON_ID;

    @Column(name = "last_event_id")
    private Long lastEventId;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash = AuditHashChain.GENESIS_HASH;

    protected AuditChainHead() {
        // for JPA; the row is seeded by migration V6 at the genesis hash
    }

    static AuditChainHead genesis() {
        return new AuditChainHead();
    }

    public Long getLastEventId() {
        return lastEventId;
    }

    public String getLastHash() {
        return lastHash;
    }

    void advanceTo(Long eventId, String entryHash) {
        this.lastEventId = eventId;
        this.lastHash = entryHash;
    }
}
