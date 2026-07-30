package com.aegis.resourcedemo.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-user profiles — the resource the ABAC ownership rule protects.
 *
 * <p>The Rego policy allows {@code /api/users/{id}} only when {@code id} equals the token
 * subject, so a caller can read their own profile and is denied someone else's <em>at the
 * gateway</em>. That is the point worth showing: this controller contains no authorization
 * logic of its own, yet cross-user access is impossible — the decision lives in versioned,
 * unit-tested policy rather than scattered through service code.
 *
 * <p>Defense in depth still applies: the JWT is re-validated here, so a request that somehow
 * bypassed the gateway would fail authentication before reaching this method.
 */
@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    @GetMapping("/{id}")
    public Map<String, Object> profile(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "aegis-resource-demo");
        body.put("profileOf", id);
        body.put("requestedBy", jwt.getSubject());
        // True whenever this method runs at all — the gateway denies every other combination.
        body.put("ownedByCaller", id.equals(jwt.getSubject()));
        return body;
    }
}
