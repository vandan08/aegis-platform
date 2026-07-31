package com.aegis.resourcedemo.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates that identity survives the full hop: client &rarr; gateway &rarr; service.
 * The {@link Jwt} here was minted by the auth server and validated twice (gateway + here).
 */
@RestController
@RequestMapping("/api/demo")
public class WhoAmIController {

    @GetMapping("/whoami")
    public Map<String, Object> whoami(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "aegis-resource-demo");
        body.put("subject", jwt.getSubject());
        body.put("issuer", String.valueOf(jwt.getIssuer()));
        body.put("scopes", jwt.getClaimAsStringList("scope"));
        body.put("roles", jwt.getClaimAsStringList("roles"));
        body.put("keyId", jwt.getHeaders().get("kid"));
        body.put("issuedAt", String.valueOf(jwt.getIssuedAt()));
        body.put("expiresAt", String.valueOf(jwt.getExpiresAt()));
        return body;
    }

    /**
     * A write endpoint, so the platform has something the time-of-day ABAC rule can govern.
     *
     * <p>The Rego policy only permits {@code POST /api/demo/**} for callers holding the
     * {@code demo.write} scope <em>and</em> only between 09:00–17:00 UTC. Reaching this method
     * at all therefore proves the gateway consulted the PDP and got an allow — a request that
     * fails either half never leaves the edge.
     */
    @PostMapping("/echo")
    public Map<String, Object> echo(@AuthenticationPrincipal Jwt jwt,
                                    @RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "aegis-resource-demo");
        body.put("writtenBy", jwt.getSubject());
        body.put("writtenAt", Instant.now().toString());
        body.put("echo", payload == null ? Map.of() : payload);
        return body;
    }
}
