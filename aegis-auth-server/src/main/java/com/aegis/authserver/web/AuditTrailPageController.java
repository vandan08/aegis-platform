package com.aegis.authserver.web;

import java.security.Principal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.aegis.authserver.audit.AuditChainVerifier;
import com.aegis.authserver.audit.AuditEventType;
import com.aegis.authserver.audit.AuditService;
import com.aegis.authserver.audit.AuthAuditEvent;
import com.aegis.authserver.audit.AuthAuditEventRepository;
import com.aegis.authserver.audit.ChainVerificationResult;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin UI for the tamper-evident audit trail: the 50 newest events plus a one-click
 * chain-integrity check (the HTML sibling of {@code GET /api/admin/audit/verify}).
 * Viewing is a plain GET; verification is a POST because it appends an
 * {@code AUDIT_CHAIN_VERIFIED} event to the very chain it checks.
 * Restricted to ROLE_ADMIN by the security config.
 */
@Controller
@RequestMapping("/admin/audit")
public class AuditTrailPageController {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final AuthAuditEventRepository events;
    private final AuditChainVerifier verifier;
    private final AuditService audit;

    public AuditTrailPageController(AuthAuditEventRepository events,
            AuditChainVerifier verifier, AuditService audit) {
        this.events = events;
        this.verifier = verifier;
        this.audit = audit;
    }

    @GetMapping
    public String view(Model model) {
        List<AuditRow> rows = events.findTop50ByOrderByOccurredAtDesc().stream()
                .map(AuditRow::of)
                .toList();
        model.addAttribute("events", rows);
        return "admin/audit";
    }

    @PostMapping("/verify")
    public String verify(Principal principal, RedirectAttributes redirect) {
        ChainVerificationResult result = verifier.verify();
        audit.record(AuditEventType.AUDIT_CHAIN_VERIFIED, principal.getName(), result.auditDetail());
        redirect.addFlashAttribute("verification", result);
        return "redirect:/admin/audit";
    }

    /** View row: timestamp pre-formatted, hash split into a display prefix + full value for the tooltip. */
    public record AuditRow(Long id, String time, String type, String principal,
            String detail, String remoteIp, String hashPrefix, String hashFull) {

        static AuditRow of(AuthAuditEvent e) {
            String hash = e.getEntryHash();
            return new AuditRow(e.getId(), TIME.format(e.getOccurredAt()), e.getEventType(),
                    e.getPrincipal(), e.getDetail(), e.getRemoteIp(),
                    hash == null ? null : hash.substring(0, 12), hash);
        }
    }
}
