package com.aegis.gateway.playground;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

/**
 * The demo console has to be reachable by someone who has no token yet — that is the whole
 * point of it — while the APIs behind it stay closed. Both halves are asserted here, because
 * getting the allow-list wrong in either direction is easy and silent: too tight and the
 * landing page 401s, too loose and a proxied route slips through unauthenticated.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.health.redis.enabled=false",
                "aegis.playground.issuer-uri=https://auth.example.test/",
                "aegis.playground.demo-user=alice"
        })
class PlaygroundEndpointsTest {

    @LocalServerPort
    int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("the console is served at the root, which is also the OAuth2 redirect URI")
    void servesConsoleAtRootAnonymously() {
        client().get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/html")
                .expectBody(String.class).value(html -> {
                    org.assertj.core.api.Assertions.assertThat(html).contains("Aegis");
                });
    }

    @Test
    @DisplayName("the config endpoint publishes the public client details, trailing slash trimmed")
    void publishesPlaygroundConfig() {
        client().get().uri("/playground/config")
                .exchange()
                .expectStatus().isOk()
                // A trailing slash here would build "https://host//oauth2/authorize", which the
                // authorization server treats as a different, unregistered endpoint.
                .expectBody()
                .jsonPath("$.issuerUri").isEqualTo("https://auth.example.test")
                .jsonPath("$.clientId").isEqualTo("aegis-web-client")
                .jsonPath("$.demoUser").isEqualTo("alice")
                .jsonPath("$.scopes").isArray();
    }

    @Test
    @DisplayName("opening the console does not open the APIs behind it")
    void proxiedRoutesStillRequireAToken() {
        client().get().uri("/api/demo/whoami")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @TestConfiguration
    static class StubDecoderConfig {
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> Mono.error(new UnsupportedOperationException("not used in this test"));
        }
    }
}
