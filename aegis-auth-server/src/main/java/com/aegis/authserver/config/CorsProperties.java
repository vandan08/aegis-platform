package com.aegis.authserver.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins allowed to call the OAuth2 protocol endpoints from a browser.
 *
 * <p>Only browser-based clients need this. {@code /oauth2/authorize} is a top-level
 * redirect and is unaffected, but the code-for-token exchange is a {@code fetch()} from
 * the console's own origin, and without an {@code Access-Control-Allow-Origin} header the
 * browser discards the response before the page ever sees it.
 *
 * <p>Kept to an explicit allow-list rather than a wildcard: the token endpoint is the one
 * place an authorization code is redeemed, so the set of origins permitted to speak to it
 * is worth stating deliberately per environment. An empty list disables CORS altogether.
 */
@ConfigurationProperties(prefix = "aegis.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    public boolean enabled() {
        return !allowedOrigins.isEmpty();
    }
}
