package com.aegis.authserver.web;

import java.security.Principal;
import java.util.Map;

import com.aegis.authserver.audit.AuditEventType;
import com.aegis.authserver.audit.AuditService;
import com.aegis.authserver.mfa.TotpService;
import com.aegis.authserver.user.AppUser;
import com.aegis.authserver.user.AppUserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * TOTP enrollment for the authenticated user. Two steps, so a user can't lock
 * themselves out by enabling MFA with a mistyped/secret-mismatched authenticator:
 * <ol>
 *   <li>{@code POST /api/mfa/enroll} — generate and store a secret (MFA still off),
 *       return the {@code otpauth://} URI to load into an authenticator app.</li>
 *   <li>{@code POST /api/mfa/activate} — confirm a valid code, which flips MFA on.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/mfa")
public class MfaController {

    private static final String ISSUER = "Aegis";

    private final AppUserRepository users;
    private final TotpService totp;
    private final AuditService audit;

    public MfaController(AppUserRepository users, TotpService totp, AuditService audit) {
        this.users = users;
        this.totp = totp;
        this.audit = audit;
    }

    @PostMapping("/enroll")
    @Transactional
    public Map<String, String> enroll(Principal principal) {
        AppUser user = currentUser(principal);
        String secret = totp.generateSecret();
        user.setMfaSecret(secret);
        user.setMfaEnabled(false); // not active until a code is confirmed
        users.save(user);
        return Map.of(
                "secret", secret,
                "otpauthUri", totp.buildOtpAuthUri(ISSUER, user.getUsername(), secret));
    }

    @PostMapping("/activate")
    @Transactional
    public ResponseEntity<Map<String, String>> activate(Principal principal,
                                                         @Valid @RequestBody OtpRequest request) {
        AppUser user = currentUser(principal);
        if (user.getMfaSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Call /enroll first");
        }
        if (!totp.verify(user.getMfaSecret(), request.code())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid code"));
        }
        user.setMfaEnabled(true);
        users.save(user);
        audit.record(AuditEventType.MFA_ENROLLED, user.getUsername(), "TOTP enabled");
        return ResponseEntity.ok(Map.of("status", "MFA enabled"));
    }

    private AppUser currentUser(Principal principal) {
        return users.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public record OtpRequest(@NotBlank String code) {
    }
}
