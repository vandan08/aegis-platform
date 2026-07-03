package com.aegis.gateway.authz;

import java.util.List;

/**
 * The {@code input} document the gateway sends to the PDP for each request. Mirrors the
 * shape the Rego policy expects: who (subject), what (action), on what (resource), and
 * under what circumstances (context).
 */
public record AuthorizationInput(Subject subject, String action, Resource resource, Context context) {

    public record Subject(String id, List<String> roles, List<String> scopes, String tenant) {
    }

    public record Resource(String path, List<String> segments) {
    }

    public record Context(String ip, int hour) {
    }
}
