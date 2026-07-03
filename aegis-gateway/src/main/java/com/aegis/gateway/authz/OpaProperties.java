package com.aegis.gateway.authz;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Open Policy Agent (OPA) Policy Decision Point.
 *
 * @param enabled      master switch; when false the gateway skips authorization (auth only)
 * @param url          base URL of the OPA server
 * @param decisionPath the data API path of the boolean {@code allow} rule
 * @param timeout      how long to wait for a decision before failing closed (deny)
 */
@ConfigurationProperties(prefix = "aegis.authz.opa")
public record OpaProperties(boolean enabled, String url, String decisionPath, Duration timeout) {

    public OpaProperties {
        if (url == null || url.isBlank()) {
            url = "http://localhost:8181";
        }
        if (decisionPath == null || decisionPath.isBlank()) {
            decisionPath = "/v1/data/aegis/authz/allow";
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofMillis(500);
        }
    }
}
