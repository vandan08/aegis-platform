package com.aegis.authserver.user;

import java.util.Set;

import com.aegis.authserver.account.PasswordPolicy;
import com.aegis.authserver.audit.AuditEventType;
import com.aegis.authserver.audit.AuditService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates local accounts: enforces the password policy, hashes the password, and
 * assigns a default role. Registration is audited.
 */
@Service
public class RegistrationService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService audit;

    public RegistrationService(AppUserRepository users, PasswordEncoder passwordEncoder,
                               PasswordPolicy passwordPolicy, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
    }

    /**
     * @throws UsernameTakenException            if the username already exists
     * @throws PasswordPolicy.WeakPasswordException if the password is too weak
     */
    @Transactional
    public AppUser register(String username, String rawPassword, Set<String> roles) {
        if (users.existsByUsername(username)) {
            throw new UsernameTakenException(username);
        }
        passwordPolicy.validate(rawPassword);

        AppUser user = new AppUser(username, passwordEncoder.encode(rawPassword));
        user.getRoles().addAll(roles.isEmpty() ? Set.of("USER") : roles);
        AppUser saved = users.save(user);

        audit.record(AuditEventType.USER_REGISTERED, username, "roles=" + saved.getRoles());
        return saved;
    }

    /** Thrown when registering a username that is already in use. */
    public static class UsernameTakenException extends RuntimeException {
        public UsernameTakenException(String username) {
            super("Username already taken: " + username);
        }
    }
}
