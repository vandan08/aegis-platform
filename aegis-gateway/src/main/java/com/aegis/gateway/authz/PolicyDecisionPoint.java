package com.aegis.gateway.authz;

import reactor.core.publisher.Mono;

/**
 * Abstraction over the external policy engine. Keeping this an interface lets the
 * enforcement filter be unit-tested with a fake decision point (no OPA/Docker needed).
 */
public interface PolicyDecisionPoint {

    /**
     * @return a hot {@link Mono} emitting {@code true} to allow, {@code false} to deny.
     *         Implementations must fail closed (emit {@code false}) on error.
     */
    Mono<Boolean> isAllowed(AuthorizationInput input);
}
