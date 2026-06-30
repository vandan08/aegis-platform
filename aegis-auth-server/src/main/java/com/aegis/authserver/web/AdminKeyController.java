package com.aegis.authserver.web;

import java.security.Principal;
import java.util.Map;

import com.aegis.authserver.audit.AuditEventType;
import com.aegis.authserver.audit.AuditService;
import com.aegis.authserver.jwk.RotatingJwkSource;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin operations on signing keys. Restricted to ROLE_ADMIN by the security config.
 * Rotating adds a new active key that immediately signs new tokens; older active keys
 * keep validating until retired, so no outstanding token is invalidated by a rotation.
 */
@RestController
@RequestMapping("/api/admin/keys")
public class AdminKeyController {

    private final RotatingJwkSource jwkSource;
    private final AuditService audit;

    public AdminKeyController(RotatingJwkSource jwkSource, AuditService audit) {
        this.jwkSource = jwkSource;
        this.audit = audit;
    }

    @PostMapping("/rotate")
    public Map<String, String> rotate(Principal principal) {
        String newKid = jwkSource.rotate();
        audit.record(AuditEventType.KEY_ROTATED, principal.getName(), "new kid=" + newKid);
        return Map.of("currentKid", newKid);
    }
}
