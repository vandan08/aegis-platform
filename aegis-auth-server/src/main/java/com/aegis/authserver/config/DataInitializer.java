package com.aegis.authserver.config;

import java.time.Duration;
import java.util.UUID;

import com.aegis.authserver.user.AppUser;
import com.aegis.authserver.user.AppUserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * Seeds the demo accounts and the demo OAuth2 clients into Postgres on first boot.
 *
 * <p>Idempotent, but not identical for every seed. Accounts are created once and then left
 * alone, so a password changed through the UI is never silently reset. The browser client,
 * by contrast, is <em>reconciled</em> on every boot: its redirect URIs and scopes come from
 * configuration, and a deployment that moves to a new public URL must have the new URI
 * registered or the login flow breaks at {@code /oauth2/authorize}. Skipping the update
 * whenever a row already existed would make the first deploy work and every later one fail.
 *
 * <p>The admin password is the documented dev credential ({@code admin}/{@code changeit});
 * it is inserted directly rather than via {@code RegistrationService} because the
 * self-service password policy (min length) intentionally does not apply to seed data.
 * DEV ONLY — do not ship these credentials.
 */
@Configuration
@EnableConfigurationProperties(DemoProperties.class)
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seedData(AppUserRepository users,
                               PasswordEncoder passwordEncoder,
                               RegisteredClientRepository clients,
                               DemoProperties demo) {
        return args -> {
            seedAdminUser(users, passwordEncoder);
            seedDemoUser(users, passwordEncoder, demo);
            seedWebClient(clients, demo);
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

    /**
     * A plain {@code USER} account for the public sandbox.
     *
     * <p>It has to be non-admin for the demo to mean anything: the Rego policy's first RBAC
     * rule allows admins everything, so an admin session would sail through every scenario
     * and never show a denial. Signed in as this user, ownership and time-of-day rules
     * actually bite.
     */
    private void seedDemoUser(AppUserRepository users, PasswordEncoder passwordEncoder, DemoProperties demo) {
        if (users.existsByUsername(demo.user())) {
            return;
        }
        AppUser user = new AppUser(demo.user(), passwordEncoder.encode(demo.password()));
        user.getRoles().add("USER");
        users.save(user);
        log.info("Seeded demo user '{}' (DEV/DEMO ONLY)", demo.user());
    }

    private void seedWebClient(RegisteredClientRepository clients, DemoProperties demo) {
        RegisteredClient existing = clients.findByClientId("aegis-web-client");
        // Reuse the primary key when there is one: save() upserts by id, so keeping the id
        // updates the existing registration in place instead of colliding on client_id.
        String id = existing != null ? existing.getId() : UUID.randomUUID().toString();

        RegisteredClient.Builder webClient = RegisteredClient.withId(id)
                .clientId("aegis-web-client")
                .clientName("Aegis Demo Console")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // public client + PKCE
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope("openid")
                .scope("profile")
                // The demo console exercises both halves of the policy: demo.read for the GET
                // scenarios, demo.write for the time-windowed POST.
                .scope("demo.read")
                .scope("demo.write")
                .clientSettings(ClientSettings.builder()
                        // A public client has no secret, so PKCE is the only thing binding the
                        // authorization code to the client that requested it. Required, not optional.
                        .requireProofKey(true)
                        // No consent screen: this client is first-party, and a consent step would
                        // add a click to the demo without changing what is granted.
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))   // short-lived by design
                        .refreshTokenTimeToLive(Duration.ofDays(1))
                        .reuseRefreshTokens(false)                       // rotate refresh tokens
                        .build());

        demo.webClientRedirectUris().forEach(uri -> webClient.redirectUri(uri.trim()));

        clients.save(webClient.build());
        log.info("Registered OAuth2 client 'aegis-web-client' with redirect URIs {}",
                demo.webClientRedirectUris());
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
