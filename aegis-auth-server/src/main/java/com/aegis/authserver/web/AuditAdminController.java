package com.aegis.authserver.web;

import java.security.Principal;

import com.aegis.authserver.audit.AuditChainVerifier;
import com.aegis.authserver.audit.AuditEventType;
import com.aegis.authserver.audit.AuditService;
import com.aegis.authserver.audit.ChainVerificationResult;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin view of the audit trail's integrity. Restricted to ROLE_ADMIN by the security
 * config. Verification recomputes the entire hash chain, so any rewrite, deletion, or
 * reordering of chained audit rows — even via direct database access — is reported.
 * Each verification is itself audited (appended to the chain it just checked); the
 * returned head hash can be exported as an external anchor.
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditAdminController {

    private final AuditChainVerifier verifier;
    private final AuditService audit;

    public AuditAdminController(AuditChainVerifier verifier, AuditService audit) {
        this.verifier = verifier;
        this.audit = audit;
    }

    @GetMapping("/verify")
    public ChainVerificationResult verify(Principal principal) {
        ChainVerificationResult result = verifier.verify();
        audit.record(AuditEventType.AUDIT_CHAIN_VERIFIED, principal.getName(), result.auditDetail());
        return result;
    }
}
