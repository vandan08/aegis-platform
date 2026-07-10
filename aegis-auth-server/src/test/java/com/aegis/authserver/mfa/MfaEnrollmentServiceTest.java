package com.aegis.authserver.mfa;

import java.time.Instant;
import java.util.Optional;

import com.aegis.authserver.audit.AuditEventType;
import com.aegis.authserver.audit.AuditService;
import com.aegis.authserver.user.AppUser;
import com.aegis.authserver.user.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two-step rule is the security property: a fresh secret must never enable MFA by
 * itself, and only a code that actually verifies flips it on (otherwise a scanning
 * mistake locks the user out at the next login).
 */
class MfaEnrollmentServiceTest {

    private final TotpService totp = new TotpService();
    private AuditService audit;
    private AppUser user;
    private MfaEnrollmentService service;

    @BeforeEach
    void setUp() {
        user = new AppUser("alice", "{noop}x");
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        audit = mock(AuditService.class);
        service = new MfaEnrollmentService(users, totp, audit);
    }

    @Test
    @DisplayName("enroll stores a pending secret but leaves MFA off")
    void enrollIsPendingNotActive() {
        MfaEnrollmentService.PendingEnrollment pending = service.enroll("alice");

        assertThat(user.getMfaSecret()).isEqualTo(pending.secret());
        assertThat(user.isMfaEnabled()).isFalse();
        assertThat(pending.otpauthUri())
                .startsWith("otpauth://totp/Aegis:alice?secret=" + pending.secret());
        assertThat(service.pending("alice")).isPresent();
    }

    @Test
    @DisplayName("a valid code activates MFA and audits the enrollment")
    void validCodeActivates() {
        String secret = service.enroll("alice").secret();
        String code = totp.codeAt(secret, Instant.now().getEpochSecond());

        assertThat(service.activate("alice", code)).isTrue();
        assertThat(user.isMfaEnabled()).isTrue();
        assertThat(service.pending("alice")).isEmpty(); // no longer pending once active
        verify(audit).record(eq(AuditEventType.MFA_ENROLLED), eq("alice"), anyString());
    }

    @Test
    @DisplayName("a wrong code is rejected and MFA stays off")
    void wrongCodeStaysPending() {
        service.enroll("alice");

        assertThat(service.activate("alice", "000000")).isFalse();
        assertThat(user.isMfaEnabled()).isFalse();
        verify(audit, never()).record(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("activation without a prior enrollment is an error")
    void activateWithoutEnrollThrows() {
        assertThatThrownBy(() -> service.activate("alice", "123456"))
                .isInstanceOf(MfaEnrollmentService.NotEnrolledException.class);
    }

    @Test
    @DisplayName("re-enrolling replaces the secret and drops back to pending")
    void reenrollReplacesSecretAndDisables() {
        String first = service.enroll("alice").secret();
        service.activate("alice", totp.codeAt(first, Instant.now().getEpochSecond()));

        String second = service.enroll("alice").secret();

        assertThat(second).isNotEqualTo(first);
        assertThat(user.isMfaEnabled()).isFalse();
    }
}
