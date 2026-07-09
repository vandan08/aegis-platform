package com.aegis.authserver.audit;

/**
 * Outcome of walking the audit hash chain. {@code legacyEvents} counts pre-chain rows
 * (written before migration V6, null hashes) that cannot be verified; {@code intact}
 * covers every chained row. On a break, {@code firstBrokenEventId} is the offending
 * row's id ({@code null} when the break is the chain head itself) and {@code reason}
 * says what failed.
 */
public record ChainVerificationResult(
        boolean intact,
        long verifiedEvents,
        long legacyEvents,
        String headHash,
        Long firstBrokenEventId,
        String reason) {

    static ChainVerificationResult intact(long verifiedEvents, long legacyEvents, String headHash) {
        return new ChainVerificationResult(true, verifiedEvents, legacyEvents, headHash, null, null);
    }

    static ChainVerificationResult broken(long verifiedEvents, long legacyEvents, String headHash,
            Long firstBrokenEventId, String reason) {
        return new ChainVerificationResult(false, verifiedEvents, legacyEvents, headHash,
                firstBrokenEventId, reason);
    }

    /** One-line summary recorded on the chain whenever a verification runs. */
    public String auditDetail() {
        return (intact ? "chain intact, " : "CHAIN BROKEN, ")
                + verifiedEvents + " events verified, head=" + headHash;
    }
}
