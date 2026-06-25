package com.aegis.authserver.account;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Bridges Spring Security's authentication events to the account-lockout bookkeeping
 * and audit trail. Relies on Boot's auto-configured {@code DefaultAuthenticationEventPublisher}.
 */
@Component
public class AuthenticationEventListener {

    private final AccountLockoutService lockout;

    public AuthenticationEventListener(AccountLockoutService lockout) {
        this.lockout = lockout;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        lockout.onSuccess(event.getAuthentication().getName(), remoteIp(event.getAuthentication()));
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        lockout.onFailure(event.getAuthentication().getName(), remoteIp(event.getAuthentication()));
    }

    @EventListener
    public void onLocked(AuthenticationFailureLockedEvent event) {
        lockout.onLockedAttempt(event.getAuthentication().getName(), remoteIp(event.getAuthentication()));
    }

    private static String remoteIp(Authentication authentication) {
        if (authentication != null
                && authentication.getDetails() instanceof WebAuthenticationDetails details) {
            return details.getRemoteAddress();
        }
        return null;
    }
}
