package com.aegis.authserver.audit;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, Short> {

    /**
     * Loads the chain head under a pessimistic write lock ({@code SELECT ... FOR UPDATE}),
     * serializing appends so the chain can never fork under concurrency.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditChainHead h where h.id = 1")
    Optional<AuditChainHead> lockHead();
}
