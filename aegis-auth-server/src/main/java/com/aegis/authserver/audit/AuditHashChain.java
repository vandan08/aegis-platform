package com.aegis.authserver.audit;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Computes the SHA-256 hash chain over audit events. Each event's {@code entry_hash} is
 * a digest of the previous event's hash plus this event's content, so modifying,
 * deleting, or reordering any chained row invalidates every hash after it.
 *
 * <p>Encoding is deliberately canonical and unambiguous: a fixed domain-separation
 * prefix, then each field as a null marker byte followed by a length-prefixed UTF-8
 * value. Length prefixes prevent field-boundary ambiguity (("ab","c") must never hash
 * like ("a","bc")), and the explicit null marker keeps {@code null} distinct from the
 * empty string. Timestamps are encoded as epoch second + nanos, which is why
 * {@link AuthAuditEvent} truncates them to microseconds — the value must survive a
 * round-trip through Postgres {@code TIMESTAMPTZ} unchanged or re-verification fails.
 */
public final class AuditHashChain {

    /** Hash the first chained event links to, before any events exist. */
    public static final String GENESIS_HASH = "0".repeat(64);

    private static final byte[] DOMAIN_PREFIX = "aegis-audit-v1".getBytes(StandardCharsets.UTF_8);

    private AuditHashChain() {
    }

    /** Convenience overload hashing a persisted event against the given predecessor hash. */
    public static String entryHash(String prevHash, AuthAuditEvent event) {
        return entryHash(prevHash, event.getOccurredAt(), event.getEventType(),
                event.getPrincipal(), event.getDetail(), event.getRemoteIp());
    }

    public static String entryHash(String prevHash, Instant occurredAt, String eventType,
            String principal, String detail, String remoteIp) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.write(DOMAIN_PREFIX);
            writeField(out, prevHash);
            out.writeLong(occurredAt.getEpochSecond());
            out.writeInt(occurredAt.getNano());
            writeField(out, eventType);
            writeField(out, principal);
            writeField(out, detail);
            writeField(out, remoteIp);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // in-memory stream; cannot happen
        }
        return HexFormat.of().formatHex(sha256().digest(buffer.toByteArray()));
    }

    private static void writeField(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            out.writeByte(0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeByte(1);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JCA spec", e);
        }
    }
}
