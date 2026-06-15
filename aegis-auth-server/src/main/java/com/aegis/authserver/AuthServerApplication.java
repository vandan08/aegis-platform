package com.aegis.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aegis Authorization Server.
 *
 * <p>Issues OAuth2 / OIDC tokens (short-lived JWTs) and is the single source of
 * truth for identity in the Aegis platform. The gateway and downstream services
 * trust tokens minted here.
 */
@SpringBootApplication
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
