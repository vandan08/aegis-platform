package com.aegis.authserver.user;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adapts a persisted {@link AppUser} to Spring Security's {@link UserDetails}.
 *
 * <p>Keeps the JPA entity free of framework concerns. Account lockout is surfaced
 * through {@link #isAccountNonLocked()} so the authentication manager rejects locked
 * accounts before any password comparison.
 */
public class AegisUserDetails implements UserDetails {

    private final AppUser user;

    public AegisUserDetails(AppUser user) {
        this.user = user;
    }

    /** The backing entity, exposed for MFA checks (secret lookup) and auditing. */
    public AppUser getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    public boolean isMfaEnabled() {
        return user.isMfaEnabled();
    }

    static List<String> normalizeRoles(List<String> roles) {
        return roles.stream().map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r).toList();
    }
}
