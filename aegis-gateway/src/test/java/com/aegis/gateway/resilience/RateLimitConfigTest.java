package com.aegis.gateway.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The rate-limit key must isolate callers: an end-user gets a {@code user:} bucket, a
 * client-credentials token gets a {@code client:} bucket, and an unauthenticated request
 * falls back to the remote IP — so no two identities ever share a token bucket.
 */
class RateLimitConfigTest {

    private final KeyResolver resolver = new RateLimitConfig().principalOrClientKeyResolver();

    @Test
    void keysUserTokenBySubject() {
        Jwt jwt = jwt("alice").build();
        String key = resolver.resolve(exchange())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(jwt)))
                .block();
        assertThat(key).isEqualTo("user:alice");
    }

    @Test
    void keysClientCredentialsTokenByClientId() {
        // client_credentials: subject == client id, and a client_id claim is present.
        Jwt jwt = jwt("aegis-service-client").claim("client_id", "aegis-service-client").build();
        String key = resolver.resolve(exchange())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth(jwt)))
                .block();
        assertThat(key).isEqualTo("client:aegis-service-client");
    }

    @Test
    void fallsBackToRemoteIpWhenNoPrincipal() {
        String key = resolver.resolve(exchange()).block();
        assertThat(key).isEqualTo("ip:203.0.113.7");
    }

    @Test
    void sameCallerGetsSameKeyRegardlessOfPathOrHeaders() {
        // Rate-limit bypass resistance: a caller must not be able to win a fresh token bucket
        // by varying the request path or by adding headers. The key derives purely from the
        // authenticated principal, so it is identical across otherwise-different requests.
        Jwt jwt = jwt("alice").build();
        var auth = ReactiveSecurityContextHolder.withAuthentication(auth(jwt));

        MockServerWebExchange a = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/demo/whoami").build());
        MockServerWebExchange b = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/demo/OTHER/path?x=1")
                .header("X-Forwarded-For", "9.9.9.9")
                .header("X-Custom", "evade").build());

        String keyA = resolver.resolve(a).contextWrite(auth).block();
        String keyB = resolver.resolve(b).contextWrite(auth).block();

        assertThat(keyA).isEqualTo("user:alice");
        assertThat(keyB).isEqualTo(keyA);
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/demo/whoami")
                .remoteAddress(new InetSocketAddress("203.0.113.7", 40000))
                .build());
    }

    private static Jwt.Builder jwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
    }

    private static JwtAuthenticationToken auth(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, List.of());
    }
}
