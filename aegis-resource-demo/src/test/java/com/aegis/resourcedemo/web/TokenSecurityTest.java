package com.aegis.resourcedemo.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;

/**
 * Adversarial tests for the resource server's JWT validation (Phase 5 security tests).
 *
 * <p>Each test forges or mangles a token the way an attacker would and asserts the service
 * answers <b>401</b>, never serving protected data. The decoder is pinned to one RSA public
 * key (the "real" signing key), so anything not signed by its private counterpart — an
 * {@code alg=none} token, a token signed by a different key, a payload tampered after signing,
 * an expired token, or one from the wrong issuer — must be rejected.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenSecurityTest {

    private static final String ISSUER = "http://localhost:9000";
    // The key the server trusts (decoder is built from its public half).
    private static final RSAKey TRUSTED_KEY = generateRsaKey();
    // A key the server does NOT trust — stands in for an attacker's own key.
    private static final RSAKey ATTACKER_KEY = generateRsaKey();

    @LocalServerPort
    int port;

    private HttpStatusCode callWith(String bearer) {
        return RestClient.create("http://localhost:" + port)
                .get().uri("/api/demo/whoami")
                .header("Authorization", "Bearer " + bearer)
                .exchange((req, res) -> res.getStatusCode());
    }

    @Test
    void rejectsAlgNoneToken() throws Exception {
        // "alg":"none" with an empty signature — the classic JWT downgrade attack.
        String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = b64("{\"sub\":\"attacker\",\"iss\":\"" + ISSUER + "\",\"scope\":\"demo.read\"}");
        String forged = header + "." + payload + ".";

        assertThat(callWith(forged).value()).isEqualTo(401);
    }

    @Test
    void rejectsTokenSignedByUntrustedKey() throws Exception {
        // Correctly RS256-signed, valid claims — but by a key the server never published.
        String forged = signWith(ATTACKER_KEY, validClaims("attacker").build());
        assertThat(callWith(forged).value()).isEqualTo(401);
    }

    @Test
    void rejectsTokenWithTamperedPayload() throws Exception {
        // Take a genuine token, swap the payload for an escalated one, keep the old signature.
        String good = signWith(TRUSTED_KEY, validClaims("alice").build());
        String[] parts = good.split("\\.");
        String tamperedPayload = b64("{\"sub\":\"alice\",\"iss\":\"" + ISSUER
                + "\",\"scope\":\"demo.read demo.write\"}");
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(callWith(tampered).value()).isEqualTo(401);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        JWTClaimsSet expired = new JWTClaimsSet.Builder(validClaims("alice").build())
                .issueTime(java.util.Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expirationTime(java.util.Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .build();
        String token = signWith(TRUSTED_KEY, expired);

        assertThat(callWith(token).value()).isEqualTo(401);
    }

    @Test
    void rejectsTokenFromWrongIssuer() throws Exception {
        JWTClaimsSet wrongIssuer = new JWTClaimsSet.Builder(validClaims("alice").build())
                .issuer("http://evil.example.com")
                .build();
        String token = signWith(TRUSTED_KEY, wrongIssuer);

        assertThat(callWith(token).value()).isEqualTo(401);
    }

    @Test
    void rejectsMalformedToken() {
        // Garbage in the Authorization header must be a clean 401, never a 500 (a parser
        // crash would be information leakage).
        assertThat(callWith("not.a.jwt").value()).isEqualTo(401);
    }

    @Test
    void acceptsAGenuineToken() {
        // Control: a properly signed, unexpired, right-issuer token IS accepted — proving the
        // rejections above are due to the attack, not a decoder that refuses everything.
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(TRUSTED_KEY)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER).subject("alice")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .claim("scope", "demo.read")
                .build();
        JwsHeader h = JwsHeader.with(() -> "RS256").keyId(TRUSTED_KEY.getKeyID()).build();
        String token = encoder.encode(JwtEncoderParameters.from(h, claims)).getTokenValue();

        assertThat(callWith(token).value()).isEqualTo(200);
    }

    // --- helpers ---------------------------------------------------------------

    private static JWTClaimsSet.Builder validClaims(String subject) {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject(subject)
                .issueTime(java.util.Date.from(Instant.now()))
                .expirationTime(java.util.Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .claim("scope", "demo.read");
    }

    private static String signWith(RSAKey key, JWTClaimsSet claims) throws JOSEException {
        JWSSigner signer = new RSASSASigner(key.toRSAPrivateKey());
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }

    private static RSAKey generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @TestConfiguration
    static class TestDecoderConfig {
        @Bean
        JwtDecoder jwtDecoder() throws Exception {
            // Trust exactly one key and enforce the issuer, mirroring the real resource server.
            NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withPublicKey(TRUSTED_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(
                    org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }
}
