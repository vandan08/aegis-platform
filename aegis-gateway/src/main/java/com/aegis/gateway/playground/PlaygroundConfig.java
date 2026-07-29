package com.aegis.gateway.playground;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Serves the demo console at the gateway root.
 *
 * <p>Mapped explicitly rather than relying on static-resource welcome-page handling, for one
 * concrete reason: the console is also the OAuth2 <em>redirect URI</em>. The authorization
 * server sends the browser back to exactly {@code https://host/} with {@code ?code=...}, and
 * anything that answers that URL with a redirect to {@code /index.html} would drop the query
 * string and strand the flow. A direct 200 keeps the code where the page can read it.
 *
 * <p>{@code RouterFunctionMapping} is ordered ahead of the gateway's own route mapping, so
 * this only claims {@code /} — proxied API paths are untouched.
 */
@Configuration
@EnableConfigurationProperties(PlaygroundProperties.class)
public class PlaygroundConfig {

    @Bean
    RouterFunction<ServerResponse> playgroundRouter(PlaygroundProperties properties) {
        ClassPathResource page = new ClassPathResource("static/index.html");
        return RouterFunctions.route(RequestPredicates.GET("/"), request -> {
            if (!properties.enabled() || !page.exists()) {
                return ServerResponse.notFound().build();
            }
            return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(page);
        });
    }
}
