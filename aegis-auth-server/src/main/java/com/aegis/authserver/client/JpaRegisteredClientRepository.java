package com.aegis.authserver.client;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * JPA-backed {@link RegisteredClientRepository}. Replaces the Phase-1
 * {@code InMemoryRegisteredClientRepository} so client registrations live in Postgres.
 *
 * <p>Conversion between {@link RegisteredClient} and the {@link Client} entity follows
 * Spring Authorization Server's reference guide: settings maps are (de)serialized with an
 * {@link ObjectMapper} configured with the Security and Authorization-Server Jackson modules.
 */
@Repository
public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    private final ClientRepository clients;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JpaRegisteredClientRepository(ClientRepository clients) {
        this.clients = clients;
        ClassLoader classLoader = JpaRegisteredClientRepository.class.getClassLoader();
        this.objectMapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
        this.objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        this.objectMapper.registerModule(durationModule());
    }

    /**
     * Teaches the mapper to read {@link Duration} back out of the settings maps.
     *
     * <p>{@code TokenSettings} is full of Durations (token TTLs). Jackson can <em>write</em> one
     * without help — it reads the public {@code getSeconds()}/{@code getNano()} getters — but
     * reading it back means writing {@code Duration}'s private final fields reflectively, and
     * {@code java.base} does not open {@code java.time} to the classpath. On JDK 16+ that throws
     * {@code InaccessibleObjectException}, so startup fails the moment a client is loaded from the
     * database rather than seeded into it — i.e. on every restart after the first, which is exactly
     * the path integration tests miss because Testcontainers always starts on an empty schema.
     *
     * <p>Deserializing from the two accessor values sidesteps reflection entirely and keeps the
     * stored JSON shape ({@code {"seconds":300,"nano":0}}) unchanged, so rows written by earlier
     * builds still load. Unknown properties — including the {@code @class} type id that default
     * typing writes — are ignored by reading the two fields by name.
     */
    private static SimpleModule durationModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Duration.class, new JsonDeserializer<Duration>() {
            @Override
            public Duration deserialize(JsonParser parser, DeserializationContext context)
                    throws IOException {
                JsonNode node = parser.readValueAsTree();
                long seconds = node.path("seconds").asLong();
                long nano = node.path("nano").asLong();
                return Duration.ofSeconds(seconds, nano);
            }
        });
        return module;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        clients.save(toEntity(registeredClient));
    }

    @Override
    public RegisteredClient findById(String id) {
        return clients.findById(id).map(this::toObject).orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return clients.findByClientId(clientId).map(this::toObject).orElse(null);
    }

    private RegisteredClient toObject(Client entity) {
        Set<String> authMethods = commaDelimitedToSet(entity.getClientAuthenticationMethods());
        Set<String> grantTypes = commaDelimitedToSet(entity.getAuthorizationGrantTypes());
        Set<String> redirectUris = commaDelimitedToSet(entity.getRedirectUris());
        Set<String> postLogoutRedirectUris = commaDelimitedToSet(entity.getPostLogoutRedirectUris());
        Set<String> scopes = commaDelimitedToSet(entity.getScopes());

        RegisteredClient.Builder builder = RegisteredClient.withId(entity.getId())
                .clientId(entity.getClientId())
                .clientIdIssuedAt(entity.getClientIdIssuedAt())
                .clientSecret(entity.getClientSecret())
                .clientSecretExpiresAt(entity.getClientSecretExpiresAt())
                .clientName(entity.getClientName())
                .clientAuthenticationMethods(s -> authMethods.forEach(
                        m -> s.add(new ClientAuthenticationMethod(m))))
                .authorizationGrantTypes(s -> grantTypes.forEach(
                        g -> s.add(new AuthorizationGrantType(g))))
                .redirectUris(s -> s.addAll(redirectUris))
                .postLogoutRedirectUris(s -> s.addAll(postLogoutRedirectUris))
                .scopes(s -> s.addAll(scopes));

        builder.clientSettings(ClientSettings.withSettings(readMap(entity.getClientSettings())).build());
        builder.tokenSettings(TokenSettings.withSettings(readMap(entity.getTokenSettings())).build());
        return builder.build();
    }

    private Client toEntity(RegisteredClient registeredClient) {
        List<String> authMethods = new ArrayList<>();
        registeredClient.getClientAuthenticationMethods()
                .forEach(m -> authMethods.add(m.getValue()));
        List<String> grantTypes = new ArrayList<>();
        registeredClient.getAuthorizationGrantTypes()
                .forEach(g -> grantTypes.add(g.getValue()));

        Client entity = new Client();
        entity.setId(registeredClient.getId());
        entity.setClientId(registeredClient.getClientId());
        // clientIdIssuedAt is optional on RegisteredClient but NOT NULL in the schema — a builder
        // that never set it would otherwise null out the entity default and fail the insert.
        // Registration time is "now" for any client we are seeing for the first time.
        entity.setClientIdIssuedAt(registeredClient.getClientIdIssuedAt() != null
                ? registeredClient.getClientIdIssuedAt()
                : Instant.now());
        entity.setClientSecret(registeredClient.getClientSecret());
        entity.setClientSecretExpiresAt(registeredClient.getClientSecretExpiresAt());
        entity.setClientName(registeredClient.getClientName());
        entity.setClientAuthenticationMethods(StringUtils.collectionToCommaDelimitedString(authMethods));
        entity.setAuthorizationGrantTypes(StringUtils.collectionToCommaDelimitedString(grantTypes));
        entity.setRedirectUris(StringUtils.collectionToCommaDelimitedString(registeredClient.getRedirectUris()));
        entity.setPostLogoutRedirectUris(
                StringUtils.collectionToCommaDelimitedString(registeredClient.getPostLogoutRedirectUris()));
        entity.setScopes(StringUtils.collectionToCommaDelimitedString(registeredClient.getScopes()));
        entity.setClientSettings(writeMap(registeredClient.getClientSettings().getSettings()));
        entity.setTokenSettings(writeMap(registeredClient.getTokenSettings().getSettings()));
        return entity;
    }

    private Map<String, Object> readMap(String data) {
        try {
            return objectMapper.readValue(data, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot deserialize client settings", ex);
        }
    }

    private String writeMap(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize client settings", ex);
        }
    }

    private static Set<String> commaDelimitedToSet(String value) {
        if (!StringUtils.hasText(value)) {
            return new HashSet<>();
        }
        return StringUtils.commaDelimitedListToSet(value);
    }
}
