package com.aegis.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import reactor.core.publisher.Mono;

/**
 * Guards the route table against silent misconfiguration.
 *
 * <p>This exists because of a real defect: the {@code cloud.gateway.*} block had been
 * indented one level too deep, under {@code aegis:} rather than {@code spring:}. Nothing
 * complained — unknown YAML keys simply bind to nothing — so the gateway started perfectly
 * with an <em>empty</em> route table and every proxied path returned 404. Startup succeeding
 * is not evidence that a gateway can route, so assert the routes directly.
 *
 * <p>Reading {@link GatewayProperties} rather than a materialized {@code RouteLocator} keeps
 * this a pure configuration-binding check: it fails for the one reason we care about here.
 */
@SpringBootTest(properties = "management.health.redis.enabled=false")
class GatewayRouteConfigurationTest {

    @Autowired
    GatewayProperties gatewayProperties;

    @Test
    @DisplayName("the demo API route is bound and points at the resource service")
    void demoRouteIsConfigured() {
        RouteDefinition route = routeById("resource-demo");

        assertThat(route.getUri()).hasToString("http://localhost:8081");
        assertThat(pathPredicates(route)).containsExactly("/api/demo/**");
    }

    @Test
    @DisplayName("the profile API route is bound and points at the resource service")
    void userProfileRouteIsConfigured() {
        RouteDefinition route = routeById("user-profile");

        assertThat(route.getUri()).hasToString("http://localhost:8081");
        assertThat(pathPredicates(route)).containsExactly("/api/users/**");
    }

    @Test
    @DisplayName("every proxied route is rate-limited and circuit-broken")
    void everyRouteCarriesTheProtectiveFilters() {
        // A new route added without these is a route that can be hammered freely and that
        // hangs when the downstream is sick — worth failing the build over, not reviewing for.
        assertThat(gatewayProperties.getRoutes()).isNotEmpty();
        for (RouteDefinition route : gatewayProperties.getRoutes()) {
            List<String> filters = route.getFilters().stream().map(FilterDefinition::getName).toList();
            assertThat(filters)
                    .as("filters on route '%s'", route.getId())
                    .contains("RequestRateLimiter", "CircuitBreaker");
        }
    }

    private RouteDefinition routeById(String id) {
        return gatewayProperties.getRoutes().stream()
                .filter(route -> id.equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No route '" + id + "'. Configured routes: "
                                + gatewayProperties.getRoutes().stream().map(RouteDefinition::getId).toList()));
    }

    private List<String> pathPredicates(RouteDefinition route) {
        return route.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .map(PredicateDefinition::getArgs)
                .flatMap(args -> args.values().stream())
                .toList();
    }

    /** Avoids the issuer discovery fetch at startup; no token is decoded in this test. */
    @TestConfiguration
    static class StubDecoderConfig {
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> Mono.error(new UnsupportedOperationException("not used in this test"));
        }
    }
}
