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
 * a small allow-list (health checks and the static demo console). Token signature/expiry
 * are validated against the issuer's JWKS (configured via
 * {@code spring.security.oauth2.resourceserver}).
 *
 * <p>Fine-grained authorization runs on top, in {@code PolicyEnforcementFilter}, which asks
 * the OPA policy engine about every request that <em>did</em> authenticate.
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
                        // The demo console (see resources/static/index.html). It is inert HTML +
                        // JS: it holds no secret, and every API call it makes still goes through
                        // the same authentication and policy checks as any other caller. The
                        // config endpoint publishes only what an OAuth2 public client discloses
                        // anyway — issuer URL, client id, requested scopes.
                        .pathMatchers("/", "/index.html", "/favicon.ico",
                                "/assets/**", "/playground/config").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
