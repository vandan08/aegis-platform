package com.aegis.gateway.playground;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the handful of values the demo console cannot know at build time.
 *
 * <p>The console is a static page, but the URLs it must talk to differ per environment
 * (localhost, a preview deploy, production). Rather than baking them into the HTML or
 * asking the operator to edit JavaScript, the page fetches them from here at load time —
 * so one image runs unchanged everywhere and the deployment is configured purely by
 * environment variables.
 *
 * <p>Nothing secret is exposed: an OAuth2 public client's id and scopes are visible in
 * every authorization request it makes, and the demo credentials are the documented
 * throwaway logins for the public sandbox.
 */
@RestController
public class PlaygroundController {

    private final PlaygroundProperties properties;

    public PlaygroundController(PlaygroundProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/playground/config")
    public ResponseEntity<Map<String, Object>> config() {
        if (!properties.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issuerUri", properties.issuerUri());
        body.put("clientId", properties.clientId());
        body.put("scopes", properties.scopes());
        body.put("demoUser", properties.demoUser());
        body.put("demoPassword", properties.demoPassword());
        return ResponseEntity.ok(body);
    }
}
