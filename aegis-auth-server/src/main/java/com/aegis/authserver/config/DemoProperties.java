package com.aegis.authserver.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seed data for the public sandbox, so a deployment can be configured without a rebuild.
 *
 * <p>All of it is DEV/DEMO ONLY — see {@code docs/ROADMAP.md}. A real deployment registers
 * clients through an admin flow and never ships a known-password account.
 *
 * @param webClientRedirectUris redirect URIs registered for the public browser client. These
 *                              must match the deployed console's URL exactly: the
 *                              authorization server compares them literally, and a mismatch
 *                              is rejected at {@code /oauth2/authorize} before login.
 * @param user                  username of the seeded non-admin demo account
 * @param password              its password (plain here, hashed before storage)
 */
@ConfigurationProperties(prefix = "aegis.demo")
public record DemoProperties(List<String> webClientRedirectUris, String user, String password) {

    public DemoProperties {
        if (webClientRedirectUris == null || webClientRedirectUris.isEmpty()) {
            webClientRedirectUris = List.of("http://localhost:8080/", "http://127.0.0.1:8080/");
        }
        if (user == null || user.isBlank()) {
            user = "alice";
        }
        if (password == null || password.isBlank()) {
            password = "aegis-demo-2026";
        }
    }
}
