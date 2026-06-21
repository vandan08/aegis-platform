package com.aegis.authserver.jwk;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One persisted RSA signing key pair. The public half of every {@code active} key is
 * published in the JWKS; the newest active key signs new tokens. Rotating means adding
 * a new active key (which becomes the signer) while keeping older ones active long
 * enough for their outstanding tokens to expire, then deactivating them.
 */
@Entity
@Table(name = "signing_key")
public class SigningKey {

    @Id
    @Column(name = "kid", nullable = false, updatable = false)
    private String kid;

    @Column(name = "public_key_pem", nullable = false, columnDefinition = "text")
    private String publicKeyPem;

    @Column(name = "private_key_pem", nullable = false, columnDefinition = "text")
    private String privateKeyPem;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SigningKey() {
        // for JPA
    }

    public SigningKey(String kid, String publicKeyPem, String privateKeyPem) {
        this.kid = kid;
        this.publicKeyPem = publicKeyPem;
        this.privateKeyPem = privateKeyPem;
    }

    public String getKid() {
        return kid;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
