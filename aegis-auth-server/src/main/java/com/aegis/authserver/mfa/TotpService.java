package com.aegis.authserver.mfa;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

/**
 * RFC 6238 TOTP (time-based one-time passwords), the algorithm behind Google
 * Authenticator, Authy, 1Password, etc. Implemented directly (HMAC-SHA1, 30-second
 * step, 6 digits) rather than pulling in a dependency — it is small and the point of
 * this project is to show the mechanics.
 */
@Service
public class TotpService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int SECRET_BYTES = 20; // 160-bit, per RFC 6238 recommendation
    /** Accept codes from this many steps before/after now to tolerate clock skew. */
    private static final int ALLOWED_DRIFT_STEPS = 1;

    private final SecureRandom random = new SecureRandom();

    /** Generate a fresh Base32-encoded shared secret for enrollment. */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    /**
     * Build the {@code otpauth://} URI an authenticator app scans as a QR code.
     *
     * @param issuer      label shown for the account provider (e.g. "Aegis")
     * @param accountName typically the username
     * @param base32Secret the shared secret
     */
    public String buildOtpAuthUri(String issuer, String accountName, String base32Secret) {
        String label = enc(issuer) + ":" + enc(accountName);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    /** Verify a submitted code against the secret, tolerating small clock drift. */
    public boolean verify(String base32Secret, String code) {
        if (base32Secret == null || code == null) {
            return false;
        }
        String candidate = code.trim();
        if (candidate.length() != DIGITS || !candidate.chars().allMatch(Character::isDigit)) {
            return false;
        }
        byte[] key = Base32.decode(base32Secret);
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (int offset = -ALLOWED_DRIFT_STEPS; offset <= ALLOWED_DRIFT_STEPS; offset++) {
            if (constantTimeEquals(candidate, generateCode(key, currentStep + offset))) {
                return true;
            }
        }
        return false;
    }

    /** The TOTP code for a secret at a specific instant. Package-private for testing. */
    String codeAt(String base32Secret, long epochSeconds) {
        return generateCode(Base32.decode(base32Secret), epochSeconds / TIME_STEP_SECONDS);
    }

    private String generateCode(byte[] key, long step) {
        byte[] data = new byte[8];
        long value = step;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute TOTP", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
