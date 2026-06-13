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

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;

/**
 * Verifies the resource server's zero-trust posture end-to-end over HTTP: no valid JWT
 * means no access, and a properly RS256-signed token is accepted and its claims echoed.
 *
 * <p>A test RSA key pair backs both the {@link JwtDecoder} bean (public key) and the
 * token minted in-test (private key), so real signature validation is exercised without
 * needing the auth server running.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WhoAmIControllerTest {

    private static final RSAKey RSA_KEY = generateRsaKey();

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void rejectsRequestWithoutToken() {
        HttpStatusCode status = client().get().uri("/api/demo/whoami")
                .exchange((request, response) -> response.getStatusCode());
        assertThat(status.value()).isEqualTo(401);
    }

    @Test
    void echoesClaimsForValidToken() {
        String token = mintToken("alice");
        ResponseEntity<String> response = client().get().uri("/api/demo/whoami")
                .header("Authorization", "Bearer " + token)
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"subject\":\"alice\"");
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
