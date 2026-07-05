package com.aegis.gateway.authz;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Zero-trust authorization at the edge: after a request has been authenticated, ask the
 * Policy Decision Point whether this subject may perform this action on this resource.
 * A deny short-circuits the request with 403 before it is ever proxied downstream.
 *
 * <p>Runs as a Spring Cloud Gateway {@link GlobalFilter}. It only enforces when a JWT
 * principal is present; unauthenticated but permitted paths (e.g. health) are already
 * handled by the security filter chain and pass through untouched.
 */
@Component
public class PolicyEnforcementFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PolicyEnforcementFilter.class);

    private final PolicyDecisionPoint pdp;
    private final OpaProperties properties;

    public PolicyEnforcementFilter(PolicyDecisionPoint pdp, OpaProperties properties) {
        this.pdp = pdp;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled()) {
            return chain.filter(exchange);
        }
        // Reduce to a real decision value first (defaulting to allow when there is no JWT
        // principal — the security chain already permitted such paths). Branching on a
        // Boolean avoids confusing "policy denied" with the empty completion of a Mono<Void>.
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> pdp.isAllowed(buildInput(exchange, auth)))
                .defaultIfEmpty(Boolean.TRUE)
                .flatMap(allowed -> allowed ? chain.filter(exchange) : deny(exchange));
    }

    private Mono<Void> deny(ServerWebExchange exchange) {
        log.info("DENY {} {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value());
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    private AuthorizationInput buildInput(ServerWebExchange exchange, JwtAuthenticationToken auth) {
        Jwt jwt = auth.getToken();

        List<String> roles = new ArrayList<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority.getAuthority().startsWith("ROLE_")) {
                roles.add(authority.getAuthority().substring("ROLE_".length()));
            }
        }
        // The auth server also stamps user access tokens with an explicit `roles` claim.
        List<String> claimRoles = jwt.getClaimAsStringList("roles");
        if (claimRoles != null) {
            claimRoles.forEach(r -> {
                if (!roles.contains(r)) {
                    roles.add(r);
                }
            });
        }

        List<String> scopes = jwt.getClaimAsStringList("scope");
        AuthorizationInput.Subject subject = new AuthorizationInput.Subject(
                jwt.getSubject(), roles, scopes == null ? List.of() : scopes,
                jwt.getClaimAsString("tenant"));

        String path = exchange.getRequest().getPath().value();
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        AuthorizationInput.Resource resource = new AuthorizationInput.Resource(path, segments);

        String ip = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : null;
        AuthorizationInput.Context context = new AuthorizationInput.Context(
                ip, OffsetDateTime.now(ZoneOffset.UTC).getHour());

        String action = exchange.getRequest().getMethod().name();
        return new AuthorizationInput(subject, action, resource, context);
    }

    @Override
    public int getOrder() {
        // Run after auth is established but before the routing/proxy filters.
        return 0;
    }
}
