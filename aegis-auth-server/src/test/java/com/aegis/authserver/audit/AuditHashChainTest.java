package com.aegis.authserver.audit;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Properties of the canonical hash encoding. If any of these fail, the chain is either
 * non-deterministic (false alarms) or forgeable (undetected tampering).
 */
class AuditHashChainTest {

    private static final Instant WHEN = Instant.parse("2026-07-07T10:15:30.123456Z");

    @Test
    @DisplayName("same input always hashes to the same 64-char hex value")
    void deterministic() {
        String h1 = AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_SUCCESS", "alice", "web login", "10.0.0.1");
        String h2 = AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_SUCCESS", "alice", "web login", "10.0.0.1");
        assertThat(h1).isEqualTo(h2).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("length-prefixed encoding: shifting a field boundary changes the hash")
    void fieldBoundariesAreUnambiguous() {
        // Concatenated field bytes are identical ("alicew" + "eb") — only the split differs.
        String h1 = AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_SUCCESS", "alice", "web", null);
        String h2 = AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_SUCCESS", "alicew", "eb", null);
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("null and empty-string fields hash differently")
    void nullIsDistinctFromEmpty() {
        String withNull = AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_FAILURE", "alice", null, null);
        String withEmpty = AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_FAILURE", "alice", "", null);
        assertThat(withNull).isNotEqualTo(withEmpty);
    }

    @Test
    @DisplayName("every field participates in the hash, including the previous hash and timestamp")
    void everyFieldMatters() {
        String base = AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_SUCCESS", "alice", "web login", "10.0.0.1");
        assertThat(AuditHashChain.entryHash("f".repeat(64), WHEN,
                "LOGIN_SUCCESS", "alice", "web login", "10.0.0.1")).isNotEqualTo(base);
        assertThat(AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN.plusNanos(1000),
                "LOGIN_SUCCESS", "alice", "web login", "10.0.0.1")).isNotEqualTo(base);
        assertThat(AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_FAILURE", "alice", "web login", "10.0.0.1")).isNotEqualTo(base);
        assertThat(AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_SUCCESS", "mallory", "web login", "10.0.0.1")).isNotEqualTo(base);
        assertThat(AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_SUCCESS", "alice", "cli login", "10.0.0.1")).isNotEqualTo(base);
        assertThat(AuditHashChain.entryHash(AuditHashChain.GENESIS_HASH, WHEN,
                "LOGIN_SUCCESS", "alice", "web login", "10.0.0.2")).isNotEqualTo(base);
    }
}
