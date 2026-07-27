package com.aegis.authserver.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Round-trips a client through the entity mapping without a database.
 *
 * <p>These cover the two failure modes that only ever appear on a <em>restart</em> against a
 * database that already holds clients — the path the Testcontainers integration tests never take,
 * because they always start on an empty schema and so only exercise the seeding (write) half.
 */
class JpaRegisteredClientRepositoryTest {

    private final ClientRepository clients = mock(ClientRepository.class);
    private final JpaRegisteredClientRepository repository = new JpaRegisteredClientRepository(clients);

    private static RegisteredClient.Builder client() {
        return RegisteredClient.withId("id-1")
                .clientId("aegis-test-client")
                .clientName("Aegis Test Client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/callback")
                .scope("demo.read")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build());
    }

    /** Saves through the repository and reads back whatever entity was persisted. */
    private RegisteredClient roundTrip(RegisteredClient registeredClient) {
        Client[] persisted = new Client[1];
        when(clients.save(any(Client.class))).thenAnswer(invocation -> {
            persisted[0] = invocation.getArgument(0);
            return persisted[0];
        });
        repository.save(registeredClient);

        when(clients.findByClientId("aegis-test-client")).thenReturn(Optional.of(persisted[0]));
        return repository.findByClientId("aegis-test-client");
    }

    @Test
    @DisplayName("token TTLs survive the trip through stored JSON")
    void durationSettingsRoundTrip() {
        RegisteredClient saved = roundTrip(client()
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .refreshTokenTimeToLive(Duration.ofDays(1))
                        .build())
                .build());

        // Reading a Duration back used to blow up with InaccessibleObjectException: Jackson wrote it
        // via its getters but could only reconstruct it by setting private final fields, which
        // java.base refuses to open. Every restart after the first died here.
        assertThat(saved.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(5));
        assertThat(saved.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(1));
        assertThat(saved.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(saved.getScopes()).containsExactly("demo.read");
    }

    @Test
    @DisplayName("a client with no issued-at gets one, since the column is NOT NULL")
    void clientIdIssuedAtDefaultsToNow() {
        Instant before = Instant.now();
        RegisteredClient saved = roundTrip(client().build());   // builder never sets clientIdIssuedAt

        assertThat(saved.getClientIdIssuedAt())
                .isNotNull()
                .isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
    }

    @Test
    @DisplayName("an explicit issued-at is preserved rather than overwritten")
    void explicitClientIdIssuedAtIsKept() {
        Instant issued = Instant.parse("2020-01-02T03:04:05Z");
        RegisteredClient saved = roundTrip(client().clientIdIssuedAt(issued).build());

        assertThat(saved.getClientIdIssuedAt()).isEqualTo(issued);
    }
}
