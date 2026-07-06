package com.aegis.gateway.authz;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import reactor.core.publisher.Mono;

/**
 * Unit tests for the gateway's Policy Enforcement Point. A fake {@link PolicyDecisionPoint}
 * stands in for OPA, so the enforce/deny behaviour is verified without any external service.
 */
class PolicyEnforcementFilterTest {

    private static final OpaProperties ENABLED = new OpaProperties(true, null, null, null);

    @Test
    void allowsRequestWhenPolicyPermits() {
        AtomicBoolean forwarded = new AtomicBoolean(false);
        GatewayFilterChain chain = exchange -> {
            forwarded.set(true);
            return Mono.empty();
        };
        MockServerWebExchange exchange = authenticatedExchange();

        filter((input) -> Mono.just(true)).filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth()))
                .block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deniesWith403WhenPolicyRejects() {
        AtomicBoolean forwarded = new AtomicBoolean(false);
        GatewayFilterChain chain = exchange -> {
            forwarded.set(true);
            return Mono.empty();
        };
        MockServerWebExchange exchange = authenticatedExchange();

        filter((input) -> Mono.just(false)).filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth()))
                .block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void skipsEnforcementWhenDisabled() {
        AtomicBoolean forwarded = new AtomicBoolean(false);
        GatewayFilterChain chain = exchange -> {
            forwarded.set(true);
            return Mono.empty();
        };
        OpaProperties disabled = new OpaProperties(false, null, null, null);
        PolicyEnforcementFilter filter = new PolicyEnforcementFilter(
                input -> Mono.just(false), disabled);

        filter.filter(authenticatedExchange(), chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth()))
                .block();

        assertThat(forwarded).isTrue(); // PDP never consulted
    }

    @Test
    void passesThroughWhenNoJwtPrincipal() {
        AtomicBoolean forwarded = new AtomicBoolean(false);
        GatewayFilterChain chain = exchange -> {
            forwarded.set(true);
            return Mono.empty();
        };
        // No security context written → the security chain already permitted this path.
        filter(input -> Mono.just(false)).filter(authenticatedExchange(), chain).block();

        assertThat(forwarded).isTrue();
    }

    private PolicyEnforcementFilter filter(PolicyDecisionPoint pdp) {
        return new PolicyEnforcementFilter(pdp, ENABLED);
    }

    private MockServerWebExchange authenticatedExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/demo/whoami").build());
    }

    private JwtAuthenticationToken jwtAuth() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("alice")
                .claim("scope", List.of("demo.read"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
