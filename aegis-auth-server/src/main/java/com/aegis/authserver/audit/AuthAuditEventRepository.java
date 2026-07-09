package com.aegis.authserver.audit;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEvent, Long> {

    List<AuthAuditEvent> findTop50ByOrderByOccurredAtDesc();

    List<AuthAuditEvent> findByPrincipalOrderByOccurredAtDesc(String principal);

    /** Chain-order page for verification: id order matches append order (head lock serializes inserts). */
    List<AuthAuditEvent> findByIdGreaterThanOrderByIdAsc(long id, Pageable pageable);
}
