package com.aegis.authserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegis.authserver.client.JpaRegisteredClientRepository;
import com.aegis.authserver.jwk.RotatingJwkSource;
import com.aegis.authserver.user.AppUserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack test of the Phase-2 auth server against a real Postgres (Testcontainers).
 * Exercises the pieces that only work when wired together: Flyway migrations, JPA schema
 * validation, the JPA-backed client repository, the persistent rotating JWK, and actual
 * token issuance via the {@code client_credentials} grant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthServerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @LocalServerPort
    int port;

    @Autowired
    AppUserRepository users;

    @Autowired
    JpaRegisteredClientRepository clients;

    @Autowired
    RotatingJwkSource jwkSource;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void seedsAdminUserAndClientsIntoPostgres() {
        assertThat(users.findByUsername("admin")).isPresent();
        assertThat(clients.findByClientId("aegis-web-client")).isNotNull();
        assertThat(clients.findByClientId("aegis-service-client")).isNotNull();
    }

    @Test
    void bootstrapsAPersistentSigningKey() {
        assertThat(jwkSource.currentKid()).isNotBlank();
    }

    @Test
    void publishesOidcDiscoveryDocument() {
        ResponseEntity<String> response = client().get()
                .uri("/.well-known/openid-configuration")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"issuer\":\"http://localhost:9000\"");
    }

    @Test
    void issuesAccessTokenViaClientCredentials() {
        ResponseEntity<String> response = client().post()
                .uri("/oauth2/token")
                .headers(h -> h.setBasicAuth("aegis-service-client", "service-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&scope=demo.read")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("access_token");
    }

    @Test
    void jwksExposesAtLeastOneKey() {
        ResponseEntity<String> response = client().get()
                .uri("/oauth2/jwks")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"kty\":\"RSA\"");
    }
}
