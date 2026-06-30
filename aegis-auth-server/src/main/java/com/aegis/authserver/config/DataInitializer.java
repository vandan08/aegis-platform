package com.aegis.authserver.config;

import java.time.Duration;
import java.util.UUID;

import com.aegis.authserver.user.AppUser;
import com.aegis.authserver.user.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * Seeds the demo admin account and the demo OAuth2 client into Postgres on first boot.
 *
 * <p>Idempotent: each seed is a no-op if the row already exists. This replaces the
 * Phase-1 in-memory user/client beans. The admin password is the documented dev
 * credential ({@code admin}/{@code changeit}); it is inserted directly rather than via
 * {@code RegistrationService} because the self-service password policy (min length)
 * intentionally does not apply to seed data. DEV ONLY — do not ship these credentials.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seedData(AppUserRepository users,
                               PasswordEncoder passwordEncoder,
                               RegisteredClientRepository clients) {
        return args -> {
            seedAdminUser(users, passwordEncoder);
            seedWebClient(clients);
            seedServiceClient(clients, passwordEncoder);
        };
    }

    private void seedAdminUser(AppUserRepository users, PasswordEncoder passwordEncoder) {
        if (users.existsByUsername("admin")) {
            return;
        }
        AppUser admin = new AppUser("admin", passwordEncoder.encode("changeit"));
        admin.getRoles().add("ADMIN");
        users.save(admin);
        log.info("Seeded demo admin user 'admin' (DEV ONLY)");
    }

    private void seedWebClient(RegisteredClientRepository clients) {
        if (clients.findByClientId("aegis-web-client") != null) {
            return;
        }
        RegisteredClient webClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("aegis-web-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // public client + PKCE
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/aegis")
                .scope("openid")
                .scope("profile")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))   // short-lived by design
                        .refreshTokenTimeToLive(Duration.ofDays(1))
                        .reuseRefreshTokens(false)                       // rotate refresh tokens
                        .build())
                .build();
        clients.save(webClient);
        log.info("Seeded demo OAuth2 client 'aegis-web-client'");
    }

    /**
     * Confidential service client using the {@code client_credentials} grant — machine-to-
     * machine access with no user. Useful for demos/smoke tests where obtaining a token via
     * the browser-based authorization-code flow would be awkward. DEV ONLY secret.
     */
    private void seedServiceClient(RegisteredClientRepository clients, PasswordEncoder passwordEncoder) {
        if (clients.findByClientId("aegis-service-client") != null) {
            return;
        }
        RegisteredClient serviceClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("aegis-service-client")
                .clientSecret(passwordEncoder.encode("service-secret")) // DEV ONLY — override in real envs
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("demo.read")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build();
        clients.save(serviceClient);
        log.info("Seeded demo OAuth2 service client 'aegis-service-client'");
    }
}
