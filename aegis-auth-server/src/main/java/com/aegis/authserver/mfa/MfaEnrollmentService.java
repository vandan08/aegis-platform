package com.aegis.authserver.mfa;

import java.util.Optional;

import com.aegis.authserver.audit.AuditEventType;
import com.aegis.authserver.audit.AuditService;
import com.aegis.authserver.user.AppUser;
import com.aegis.authserver.user.AppUserRepository;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-step TOTP enrollment, shared by the JSON API ({@code /api/mfa/**}) and the
 * account page ({@code /account/mfa}) so both entry points enforce identical rules:
 * a fresh secret is stored <em>disabled</em>, and MFA only turns on once the user
 * proves their authenticator produces a valid code — no locking yourself out with a
 * mistyped secret. Activation is audited.
 */
@Service
public class MfaEnrollmentService {

    private static final String ISSUER = "Aegis";

    private final AppUserRepository users;
    private final TotpService totp;
    private final AuditService audit;

    public MfaEnrollmentService(AppUserRepository users, TotpService totp, AuditService audit) {
        this.users = users;
        this.totp = totp;
        this.audit = audit;
    }

    /** The shared secret plus the {@code otpauth://} URI an authenticator app scans. */
    public record PendingEnrollment(String secret, String otpauthUri) {
    }

    /** Generates and stores a new secret with MFA off; any previous secret is replaced. */
    @Transactional
    public PendingEnrollment enroll(String username) {
        AppUser user = require(username);
        String secret = totp.generateSecret();
        user.setMfaSecret(secret);
        user.setMfaEnabled(false); // not active until a code is confirmed
        users.save(user);
        return new PendingEnrollment(secret, totp.buildOtpAuthUri(ISSUER, username, secret));
    }

    /** The not-yet-activated enrollment, if one exists — lets the UI re-show the QR. */
    @Transactional(readOnly = true)
    public Optional<PendingEnrollment> pending(String username) {
        AppUser user = require(username);
        if (user.isMfaEnabled() || user.getMfaSecret() == null) {
            return Optional.empty();
        }
        return Optional.of(new PendingEnrollment(user.getMfaSecret(),
                totp.buildOtpAuthUri(ISSUER, username, user.getMfaSecret())));
    }

    /**
     * Confirms the code and switches MFA on.
     *
     * @return false if the code is wrong (enrollment stays pending)
     * @throws NotEnrolledException if {@link #enroll} was never called
     */
    @Transactional
    public boolean activate(String username, String code) {
        AppUser user = require(username);
        if (user.getMfaSecret() == null) {
            throw new NotEnrolledException();
        }
        if (!totp.verify(user.getMfaSecret(), code)) {
            return false;
        }
        user.setMfaEnabled(true);
        users.save(user);
        audit.record(AuditEventType.MFA_ENROLLED, user.getUsername(), "TOTP enabled");
        return true;
    }

    private AppUser require(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    /** Thrown when activation is attempted before any enrollment. */
    public static class NotEnrolledException extends RuntimeException {
        public NotEnrolledException() {
            super("No pending MFA enrollment — enroll first");
        }
    }
}
