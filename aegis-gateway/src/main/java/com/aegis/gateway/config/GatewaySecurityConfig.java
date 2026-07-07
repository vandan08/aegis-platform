package com.aegis.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Zero-trust request authentication at the edge.
 *
 * <p>Every request must carry a valid JWT issued by the Aegis auth server, except
 * a small allow-list (health checks). Token signature/expiry are validated against
 * the issuer's JWKS (configured via {@code spring.security.oauth2.resourceserver}).
 *
 * <p>Phase 3 adds fine-grained authorization by calling out to a policy engine
 * (OPA / Cerbos) from a gateway filter.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // stateless API; no browser session/cookies
                .authorizeExchange(exchange -> exchange
                        // Health + Prometheus scrape endpoint reachable without a JWT so the
                        // metrics collector can pull them; everything else needs a valid token.
                        .pathMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
