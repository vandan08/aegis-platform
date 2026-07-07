package com.aegis.gateway.resilience;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 4: local fallbacks served when a downstream circuit breaker is open.
 *
 * <p>The {@code CircuitBreaker} gateway filter forwards here (via {@code fallbackUri})
 * when the breaker trips, so callers get a clean {@code 503} with a machine-readable
 * body instead of a hung connection or a leaked stack trace.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/resource-demo")
    ResponseEntity<Map<String, Object>> resourceDemoFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "service_unavailable",
                        "service", "aegis-resource-demo",
                        "message", "The resource service is temporarily unavailable. Please retry shortly."));
    }
}
