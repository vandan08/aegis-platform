package com.aegis.authserver.account;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Account-lockout tuning. After {@code maxAttempts} consecutive failed logins an
 * account is locked for {@code lockoutDuration}; a successful login resets the counter.
 */
@ConfigurationProperties(prefix = "aegis.security.lockout")
public record LockoutProperties(int maxAttempts, Duration lockoutDuration) {

    public LockoutProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (lockoutDuration == null || lockoutDuration.isZero() || lockoutDuration.isNegative()) {
            lockoutDuration = Duration.ofMinutes(15);
        }
    }
}
