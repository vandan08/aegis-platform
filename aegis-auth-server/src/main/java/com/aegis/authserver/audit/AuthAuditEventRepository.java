package com.aegis.authserver.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEvent, Long> {

    List<AuthAuditEvent> findTop50ByOrderByOccurredAtDesc();

    List<AuthAuditEvent> findByPrincipalOrderByOccurredAtDesc(String principal);
}
