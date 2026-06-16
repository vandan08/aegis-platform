package com.aegis.authserver.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * OAuth2 / OIDC Authorization Server configuration.
 *
 * <p><b>Phase 2:</b> the previously in-memory pieces now live in Postgres:
 * <ul>
 *   <li>Registered clients &rarr; {@code JpaRegisteredClientRepository} (com.aegis.authserver.client).</li>
 *   <li>Signing keys &rarr; {@code RotatingJwkSource} (com.aegis.authserver.jwk) — persistent and rotatable.</li>
 * </ul>
 * Both beans are contributed from their own packages, so Spring Boot's authorization-server
 * auto-configuration backs off and uses them.
 */
@Configuration
public class AuthorizationServerConfig {

    /**
     * Security filter chain dedicated to the OAuth2 Authorization Server protocol
     * endpoints (/oauth2/authorize, /oauth2/token, /.well-known/*, etc.).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // Spring Security 7 removed OAuth2AuthorizationServerConfiguration.applyDefaultSecurity().
        // The idiomatic replacement is to register the configurer via http.with(...) and scope
        // this chain to just the protocol endpoints with the configurer's endpoint matcher.
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        RequestMatcher endpointsMatcher = authorizationServer.getEndpointsMatcher();

        http
                .securityMatcher(endpointsMatcher)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults())) // enable OpenID Connect 1.0
                // Redirect browser clients that hit a protocol endpoint unauthenticated to the login page.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new LoginUrlAuthenticationEntryPoint("/login")))
                // Accept our own access tokens at the OIDC UserInfo / Client Registration endpoints.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * JwtDecoder for the auth server's own resource-server endpoints (OIDC UserInfo),
     * backed by the same rotating JWK source used to sign tokens.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}
