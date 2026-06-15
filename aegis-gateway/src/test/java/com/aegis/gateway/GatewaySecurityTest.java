package com.aegis.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

/**
 * The gateway is the zero-trust Policy Enforcement Point: a request without a valid
 * Aegis JWT must be rejected at the edge (401) before any downstream forwarding.
 *
 * <p>A stub {@link ReactiveJwtDecoder} avoids the issuer discovery fetch at startup;
 * the unauthenticated request is rejected by the security filter before the decoder or
 * the downstream route is ever reached.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // No Redis is running in this test; drop it from the health aggregate so /health
        // reflects only the app itself (the point is that security permits it unauthenticated).
        properties = "management.health.redis.enabled=false")
class GatewaySecurityTest {

    @LocalServerPort
    int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void rejectsRequestWithoutToken() {
        client().get().uri("/api/demo/whoami")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void allowsHealthEndpointWithoutToken() {
        client().get().uri("/actuator/health")
                .exchange()
                .expectStatus().is2xxSuccessful();
    }

    @TestConfiguration
    static class StubDecoderConfig {
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> Mono.error(new UnsupportedOperationException("not used in this test"));
        }
    }
}
