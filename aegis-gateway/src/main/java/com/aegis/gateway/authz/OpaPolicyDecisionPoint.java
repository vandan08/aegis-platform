package com.aegis.gateway.authz;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * {@link PolicyDecisionPoint} backed by an OPA server. Sends {@code {"input": ...}} to the
 * configured decision path and reads back {@code {"result": <bool>}}.
 *
 * <p>Zero-trust means failing closed: any error (OPA down, timeout, malformed response,
 * or a policy that returns no decision) results in {@code false} (deny).
 */
@Component
public class OpaPolicyDecisionPoint implements PolicyDecisionPoint {

    private static final Logger log = LoggerFactory.getLogger(OpaPolicyDecisionPoint.class);

    private final WebClient webClient;
    private final OpaProperties properties;

    public OpaPolicyDecisionPoint(WebClient opaWebClient, OpaProperties properties) {
        this.webClient = opaWebClient;
        this.properties = properties;
    }

    @Override
    public Mono<Boolean> isAllowed(AuthorizationInput input) {
        return webClient.post()
                .uri(properties.decisionPath())
                .bodyValue(Map.of("input", input))
                .retrieve()
                .bodyToMono(OpaResponse.class)
                .map(OpaResponse::allowed)
                .timeout(properties.timeout())
                .onErrorResume(ex -> {
                    log.warn("PDP call failed ({}); failing closed (deny)", ex.toString());
                    return Mono.just(false);
                })
                .defaultIfEmpty(false);
    }

    /** OPA's data API response envelope: {@code {"result": true}}. */
    record OpaResponse(Boolean result) {
        boolean allowed() {
            return Boolean.TRUE.equals(result);
        }
    }
}
