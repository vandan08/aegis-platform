package com.aegis.resourcedemo.web;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
        return Map.of(
                "subject", jwt.getSubject(),
                "issuer", String.valueOf(jwt.getIssuer()),
                "scopes", jwt.getClaimAsStringList("scope"),
                "issuedAt", String.valueOf(jwt.getIssuedAt()),
                "expiresAt", String.valueOf(jwt.getExpiresAt()));
    }
}
