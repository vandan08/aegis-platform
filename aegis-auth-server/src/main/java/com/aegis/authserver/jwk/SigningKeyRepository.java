package com.aegis.authserver.jwk;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyRepository extends JpaRepository<SigningKey, String> {

    /** Active keys, newest first — index 0 is the current signing key. */
    List<SigningKey> findByActiveTrueOrderByCreatedAtDesc();

    Optional<SigningKey> findFirstByActiveTrueOrderByCreatedAtDesc();
}
