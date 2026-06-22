package com.aegis.authserver.jwk;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * The single {@link OAuth2TokenCustomizer} for issued JWTs. It does two things:
 *
 * <ol>
 *   <li><b>Stamps the current signing key's {@code kid}</b> on the JWS header. This is what
 *       makes key rotation safe — several keys can be published in the JWKS at once, but
 *       pinning the {@code kid} lets Nimbus select exactly one key to sign with.</li>
 *   <li><b>Adds a {@code roles} claim</b> to access tokens, derived from the authenticated
 *       principal's {@code ROLE_} authorities. Downstream authorization (the gateway's OPA
 *       policy, Phase 3) uses these roles for RBAC decisions.</li>
 * </ol>
 *
 * <p>Spring Authorization Server uses a single {@code OAuth2TokenCustomizer<JwtEncodingContext>}
 * bean, so both concerns live here rather than in separate customizers.
 */
@Component
public class AegisJwtCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final RotatingJwkSource jwkSource;

    public AegisJwtCustomizer(RotatingJwkSource jwkSource) {
        this.jwkSource = jwkSource;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        String kid = jwkSource.currentKid();
        if (kid != null) {
            context.getJwsHeader().keyId(kid);
        }

        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            List<String> roles = context.getPrincipal().getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring("ROLE_".length()))
                    .toList();
            if (!roles.isEmpty()) {
                context.getClaims().claim("roles", roles);
            }
        }
    }
}
