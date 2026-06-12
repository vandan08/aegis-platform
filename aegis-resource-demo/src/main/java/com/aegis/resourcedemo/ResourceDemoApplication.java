package com.aegis.resourcedemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aegis Resource Demo — a downstream microservice behind the gateway.
 *
 * <p>Re-validates the JWT locally (defense in depth: never trust that the gateway
 * is the only way in) and exposes a protected endpoint that echoes identity claims.
 */
@SpringBootApplication
public class ResourceDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourceDemoApplication.class, args);
    }
}
