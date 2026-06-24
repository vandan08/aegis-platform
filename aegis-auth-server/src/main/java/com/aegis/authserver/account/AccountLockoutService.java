package com.aegis.authserver.account;

import java.time.Instant;

import com.aegis.authserver.audit.AuditEventType;
import com.aegis.authserver.audit.AuditService;
import com.aegis.authserver.user.AppUser;
import com.aegis.authserver.user.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the failed-login counter and lockout deadline on {@link AppUser}, and
 * audits the outcome. A locked account is rejected by {@code AegisUserDetails
 * .isAccountNonLocked()} before any password comparison.
 */
@Service
public class AccountLockoutService {

    private static final Logger log = LoggerFactory.getLogger(AccountLockoutService.class);

    private final AppUserRepository users;
    private final AuditService audit;
    private final LockoutProperties properties;

    public AccountLockoutService(AppUserRepository users, AuditService audit, LockoutProperties properties) {
        this.users = users;
        this.audit = audit;
        this.properties = properties;
    }

    @Transactional
    public void onSuccess(String username, String remoteIp) {
        users.findByUsername(username).ifPresent(user -> {
            if (user.getFailedLoginAttempts() != 0 || user.getLockoutUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockoutUntil(null);
                users.save(user);
            }
        });
        audit.record(AuditEventType.LOGIN_SUCCESS, username, "login succeeded", remoteIp);
    }

    @Transactional
    public void onFailure(String username, String remoteIp) {
        AppUser user = users.findByUsername(username).orElse(null);
        if (user == null) {
            // Do not disclose whether the username exists; still record the attempt.
            audit.record(AuditEventType.LOGIN_FAILURE, username, "bad credentials (unknown user)", remoteIp);
            return;
        }

        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        String detail = "bad credentials (attempt " + attempts + "/" + properties.maxAttempts() + ")";

        if (attempts >= properties.maxAttempts()) {
            Instant until = Instant.now().plus(properties.lockoutDuration());
            user.setLockoutUntil(until);
            users.save(user);
            log.warn("Account '{}' locked until {} after {} failed attempts", username, until, attempts);
            audit.record(AuditEventType.LOGIN_FAILURE, username, detail, remoteIp);
            audit.record(AuditEventType.ACCOUNT_LOCKED, username, "locked until " + until, remoteIp);
        } else {
            users.save(user);
            audit.record(AuditEventType.LOGIN_FAILURE, username, detail, remoteIp);
        }
    }

    @Transactional
    public void onLockedAttempt(String username, String remoteIp) {
        audit.record(AuditEventType.LOGIN_FAILURE, username, "attempt while locked", remoteIp);
    }
}
