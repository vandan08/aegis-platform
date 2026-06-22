package com.aegis.authserver.jwk;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistent, rotatable JWK source. Replaces the Phase-1 in-memory key that was
 * regenerated on every boot (which invalidated all outstanding tokens on restart).
 *
 * <p>All {@code active} keys from the database are published in the JWKS, so a token
 * signed by any not-yet-retired key still validates. New tokens are signed with the
 * newest active key — its {@code kid} is stamped on the JWS header by
 * {@link AegisJwtCustomizer} so Nimbus selects exactly one key even while several are
 * published during a rotation overlap window.
 */
@Component
public class RotatingJwkSource implements JWKSource<SecurityContext> {

    private static final Logger log = LoggerFactory.getLogger(RotatingJwkSource.class);

    private final SigningKeyRepository keys;

    /** Snapshot of the published JWKS, swapped atomically on (re)load and rotation. */
    private final AtomicReference<JWKSet> jwkSet = new AtomicReference<>(new JWKSet());
    /** kid of the newest active key — the one that signs new tokens. */
    private final AtomicReference<String> currentKid = new AtomicReference<>();

    public RotatingJwkSource(SigningKeyRepository keys) {
        this.keys = keys;
    }

    @PostConstruct
    @Transactional
    public void init() {
        if (keys.findFirstByActiveTrueOrderByCreatedAtDesc().isEmpty()) {
            SigningKey generated = keys.save(generateKey());
            log.info("No signing key found — bootstrapped initial key kid={}", generated.getKid());
        }
        reload();
    }

    @Override
    public List<com.nimbusds.jose.jwk.JWK> get(com.nimbusds.jose.jwk.JWKSelector jwkSelector,
                                               SecurityContext context) {
        return jwkSelector.select(jwkSet.get());
    }

    /** The kid new tokens should be signed with. */
    public String currentKid() {
        return currentKid.get();
    }

    /**
     * Rotate in a fresh signing key. The new key becomes the signer immediately while
     * existing active keys keep validating until they are explicitly deactivated.
     *
     * @return the kid of the newly generated key
     */
    @Transactional
    public String rotate() {
        SigningKey generated = keys.save(generateKey());
        reload();
        log.info("Rotated signing key — new current kid={}", generated.getKid());
        return generated.getKid();
    }

    /** Rebuild the in-memory JWKS from the active keys in the database. */
    @Transactional(readOnly = true)
    public void reload() {
        List<SigningKey> active = keys.findByActiveTrueOrderByCreatedAtDesc();
        List<com.nimbusds.jose.jwk.JWK> jwks = active.stream()
                .map(RotatingJwkSource::toRsaKey)
                .map(k -> (com.nimbusds.jose.jwk.JWK) k)
                .toList();
        jwkSet.set(new JWKSet(jwks));
        currentKid.set(active.isEmpty() ? null : active.get(0).getKid());
    }

    private static RSAKey toRsaKey(SigningKey key) {
        RSAPublicKey publicKey = PemUtils.publicKeyFromPem(key.getPublicKeyPem());
        RSAPrivateKey privateKey = PemUtils.privateKeyFromPem(key.getPrivateKeyPem());
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(key.getKid())
                .build();
    }

    private static SigningKey generateKey() {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        return new SigningKey(
                UUID.randomUUID().toString(),
                PemUtils.toPem(publicKey),
                PemUtils.toPem(privateKey));
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate RSA key pair", ex);
        }
    }
}
