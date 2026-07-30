package com.aegis.resourcedemo.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;

/**
 * Pins down where profile authorization lives — and where it deliberately does not.
 *
 * <p>Cross-user access is refused by the gateway's policy engine, so this service holds no
 * ownership check of its own. That is a design decision worth a test rather than a comment:
 * reached directly with a valid token, {@code /api/users/{someone-else}} <em>answers</em>.
 * If a future change quietly starts depending on this service to enforce ownership, the
 * second test below documents that it does not, and the enforcement must stay at the edge
 * (or be added here deliberately).
 *
 * <p>Authentication, by contrast, is enforced here — defense in depth means a request that
 * bypasses the gateway entirely still needs a validly signed Aegis token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserProfileControllerTest {

    private static final RSAKey RSA_KEY = generateRsaKey();

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    @DisplayName("a profile request without a token is rejected, gateway or no gateway")
    void rejectsRequestWithoutToken() {
        HttpStatusCode status = client().get().uri("/api/users/alice")
                .exchange((request, response) -> response.getStatusCode());
        assertThat(status.value()).isEqualTo(401);
    }

    @Test
    @DisplayName("the owner's own profile reports the caller as the owner")
    void marksOwnProfileAsOwned() {
        ResponseEntity<String> response = client().get().uri("/api/users/alice")
                .header("Authorization", "Bearer " + mintToken("alice"))
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .contains("\"profileOf\":\"alice\"")
                .contains("\"ownedByCaller\":true");
    }

    @Test
    @DisplayName("ownership is the gateway's job: reached directly, this service still answers")
    void doesNotEnforceOwnershipItself() {
        ResponseEntity<String> response = client().get().uri("/api/users/mallory")
                .header("Authorization", "Bearer " + mintToken("alice"))
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"ownedByCaller\":false");
    }

    private static String mintToken(String subject) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(RSA_KEY)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost:9000")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .claim("scope", "demo.read")
                .build();
        JwsHeader header = JwsHeader.with(() -> "RS256").keyId(RSA_KEY.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
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
            return NimbusJwtDecoder.withPublicKey(RSA_KEY.toRSAPublicKey()).build();
        }
    }
}
