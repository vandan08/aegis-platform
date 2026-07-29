package com.aegis.gateway.authz;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    /**
     * Response header stating what the PDP decided. It lets a caller (and the demo console)
     * tell a policy deny apart from an authentication failure or a downstream service's own
     * 403 — three very different problems that otherwise look identical from outside.
     */
    static final String DECISION_HEADER = "X-Aegis-Policy-Decision";

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
                .map(auth -> buildInput(exchange, auth))
                .flatMap(input -> pdp.isAllowed(input).map(allowed -> new Decision(allowed, input)))
                .defaultIfEmpty(Decision.PASS_THROUGH)
                .flatMap(decision -> {
                    if (decision.input() == null) {
                        return chain.filter(exchange);
                    }
                    if (decision.allowed()) {
                        stampAllowHeader(exchange);
                        return chain.filter(exchange);
                    }
                    return deny(exchange, decision.input());
                });
    }

    /** A policy outcome together with the input it was decided from ({@code null} = no JWT). */
    private record Decision(boolean allowed, AuthorizationInput input) {
        static final Decision PASS_THROUGH = new Decision(true, null);
    }

    /**
     * Adds the decision header just before the response commits. Setting it eagerly would not
     * survive proxying: the routing filter copies the downstream response headers onto the
     * exchange, so anything written beforehand is lost on an allowed request.
     */
    private void stampAllowHeader(ServerWebExchange exchange) {
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(DECISION_HEADER, "allow");
            return Mono.empty();
        });
    }

    /**
     * Short-circuits with 403 and a body describing exactly what was evaluated.
     *
     * <p>The body echoes only claim-derived facts the caller already holds in their own token
     * (subject, roles, scopes) plus the request they just made — never the token itself, and
     * never the policy's internals. That is enough to understand a refusal without handing an
     * attacker a map of the ruleset.
     */
    private Mono<Void> deny(ServerWebExchange exchange, AuthorizationInput input) {
        log.info("DENY {} {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value());
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(DECISION_HEADER, "deny");

        String json = "{\"error\":\"access_denied\""
                + ",\"message\":\"The policy engine denied this request.\""
                + ",\"evaluated\":{"
                + "\"subject\":" + quote(input.subject().id())
                + ",\"roles\":" + array(input.subject().roles())
                + ",\"scopes\":" + array(input.subject().scopes())
                + ",\"action\":" + quote(input.action())
                + ",\"path\":" + quote(input.resource().path())
                + ",\"utcHour\":" + input.context().hour()
                + "}}";

        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(json.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Renders one JSON string literal. Hand-rolled rather than mapper-driven: the gateway
     * starter wires codecs, not a reusable {@code ObjectMapper} bean, and this is the only
     * JSON the gateway ever writes. Escaping is still done properly — the values include a
     * caller-controlled request path, so unescaped output would be a JSON injection.
     */
    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String array(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(values.get(i)));
        }
        return out.append(']').toString();
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
