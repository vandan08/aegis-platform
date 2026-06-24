package com.aegis.authserver.account;

import org.springframework.stereotype.Component;

/**
 * Minimal but real password policy: length plus at least one letter and one digit.
 * Rejecting weak passwords at registration is cheaper than dealing with the fallout.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    /**
     * @throws WeakPasswordException if the password does not satisfy the policy
     */
    public void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new WeakPasswordException("Password must be at least " + MIN_LENGTH + " characters");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new WeakPasswordException("Password must contain at least one letter and one digit");
        }
    }

    /** Thrown when a candidate password fails {@link PasswordPolicy#validate(String)}. */
    public static class WeakPasswordException extends RuntimeException {
        public WeakPasswordException(String message) {
            super(message);
        }
    }
}
