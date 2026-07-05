package com.aegis.gateway.authz;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wiring for the authorization layer: enables {@link OpaProperties} and builds the
 * {@link WebClient} the {@link OpaPolicyDecisionPoint} uses to reach the OPA server.
 */
@Configuration
@EnableConfigurationProperties(OpaProperties.class)
public class AuthzConfig {

    @Bean
    WebClient opaWebClient(OpaProperties properties) {
        // Build directly rather than via WebClient.Builder: the gateway starter does not
        // pull in the auto-configured builder, and we only need a simple base-URL client.
        return WebClient.create(properties.url());
    }
}
