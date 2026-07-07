package com.aegis.gateway.resilience;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Phase 4: rate-limit buckets keyed by <em>caller identity</em>, not by route.
 *
 * <p>The default gateway limiter keys every request on the same route into one shared
 * bucket, so a single abusive caller can starve everyone. Instead we resolve a stable
 * key from the authenticated principal:
 * <ul>
 *   <li>end-user tokens → {@code user:<sub>}</li>
 *   <li>client-credentials tokens → {@code client:<client_id>}</li>
 *   <li>no principal (should not happen past the security chain) → {@code ip:<remote>}</li>
 * </ul>
 * Each identity therefore gets its own token bucket.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    KeyResolver principalOrClientKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> keyFor(token.getToken()))
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + remoteAddress(exchange)));
    }

    private static String keyFor(Jwt jwt) {
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId == null) {
            clientId = jwt.getClaimAsString("azp");
        }
        String subject = jwt.getSubject();
        // client_credentials tokens have no distinct end-user: the subject is the client id.
        if (clientId != null && clientId.equals(subject)) {
            return "client:" + clientId;
        }
        return "user:" + subject;
    }

    private static String remoteAddress(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}
