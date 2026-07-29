package com.aegis.gateway.playground;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings the demo console needs in order to drive a real OAuth2 login.
 *
 * <p>These are deliberately the <em>public</em> half of an OAuth2 public client: the issuer
 * URL, the client id, and the scopes to request. A public client has no secret by
 * definition, which is exactly why the browser flow uses PKCE — the authorization code is
 * useless to anyone who did not generate the verifier.
 *
 * @param enabled    master switch; turn the console off entirely in a real deployment
 * @param issuerUri  public base URL of the Aegis auth server, as the browser can reach it
 * @param clientId   the registered public client the console authenticates as
 * @param scopes     scopes requested at authorization time
 * @param demoUser   username shown on the page so a visitor can sign in (public demo only)
 * @param demoPassword password shown alongside it (public demo only; never a real secret)
 */
@ConfigurationProperties(prefix = "aegis.playground")
public record PlaygroundProperties(boolean enabled, String issuerUri, String clientId,
                                   List<String> scopes, String demoUser, String demoPassword) {

    public PlaygroundProperties {
        if (issuerUri == null || issuerUri.isBlank()) {
            issuerUri = "http://localhost:9000";
        }
        if (clientId == null || clientId.isBlank()) {
            clientId = "aegis-web-client";
        }
        if (scopes == null || scopes.isEmpty()) {
            scopes = List.of("openid", "profile", "demo.read", "demo.write");
        }
        // Trailing slashes would produce "https://host//oauth2/authorize", which some
        // authorization servers treat as a different (unregistered) endpoint.
        while (issuerUri.endsWith("/")) {
            issuerUri = issuerUri.substring(0, issuerUri.length() - 1);
        }
    }
}
