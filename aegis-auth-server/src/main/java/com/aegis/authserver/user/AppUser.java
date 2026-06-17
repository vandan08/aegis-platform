package com.aegis.authserver.user;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * A persisted local account. Replaces the Phase-1 in-memory user.
 *
 * <p>Carries the state needed for the Phase-2 account lifecycle: password policy
 * (hashed only), account lockout (failed-attempt counter + a lockout deadline),
 * and TOTP-based MFA (a per-user shared secret, only meaningful when enabled).
 * The schema is owned by Flyway (see {@code db/migration}); Hibernate only validates it.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /** BCrypt (delegating-encoder) hash — never the raw password. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    /** Base32 TOTP secret. Null until the user enrolls a second factor. */
    @Column(name = "mfa_secret")
    private String mfaSecret;

    /** Consecutive failed login attempts; reset to 0 on any success. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** When set and in the future, the account is temporarily locked. */
    @Column(name = "lockout_until")
    private Instant lockoutUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    private Set<String> roles = new LinkedHashSet<>();

    protected AppUser() {
        // for JPA
    }

    public AppUser(String username, String passwordHash) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
    }

    /** True when a lockout deadline is set and has not yet passed. */
    public boolean isLocked() {
        return lockoutUntil != null && lockoutUntil.isAfter(Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        touch();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        touch();
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
        touch();
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public void setMfaSecret(String mfaSecret) {
        this.mfaSecret = mfaSecret;
        touch();
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
        touch();
    }

    public Instant getLockoutUntil() {
        return lockoutUntil;
    }

    public void setLockoutUntil(Instant lockoutUntil) {
        this.lockoutUntil = lockoutUntil;
        touch();
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
