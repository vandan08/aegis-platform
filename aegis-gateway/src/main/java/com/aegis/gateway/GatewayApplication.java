package com.aegis.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aegis Gateway.
 *
 * <p>The single entry point for client traffic and the zero-trust policy
 * enforcement point (PEP): every request is authenticated against an
 * Aegis-issued JWT before it is allowed to reach a downstream service.
 * Authorization decisions (Phase 3) are delegated to a policy engine (OPA/Cerbos).
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
