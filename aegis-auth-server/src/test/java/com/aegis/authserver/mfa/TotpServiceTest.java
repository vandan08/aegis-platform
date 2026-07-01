package com.aegis.authserver.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the RFC 6238 TOTP implementation — no Spring context needed.
 */
class TotpServiceTest {

    private final TotpService totp = new TotpService();

    // RFC 6238 Appendix B reference secret "12345678901234567890" (ASCII), Base32-encoded.
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesRfc6238ReferenceVector() {
        // At Unix time 59s (step 1), the SHA-1/6-digit TOTP is the low 6 digits of 94287082.
        assertThat(totp.codeAt(RFC_SECRET, 59L)).isEqualTo("287082");
    }

    @Test
    void generatedSecretRoundTrips() {
        String secret = totp.generateSecret();
        String currentCode = totp.codeAt(secret, Instant.now().getEpochSecond());
        assertThat(totp.verify(secret, currentCode)).isTrue();
    }

    @Test
    void rejectsWrongCode() {
        String secret = totp.generateSecret();
        assertThat(totp.verify(secret, "000000")).isFalse();
    }

    @Test
    void rejectsMalformedCode() {
        String secret = totp.generateSecret();
        assertThat(totp.verify(secret, "12ab")).isFalse();
        assertThat(totp.verify(secret, null)).isFalse();
    }

    @Test
    void otpAuthUriContainsSecretAndIssuer() {
        String uri = totp.buildOtpAuthUri("Aegis", "admin", RFC_SECRET);
        assertThat(uri).startsWith("otpauth://totp/Aegis:admin");
        assertThat(uri).contains("secret=" + RFC_SECRET);
        assertThat(uri).contains("issuer=Aegis");
    }
}
