package com.aegis.authserver.audit;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-walks the audit hash chain from genesis and recomputes every entry hash, proving
 * that no chained row has been modified, deleted, reordered, or injected — even by
 * someone with direct database access. The chain-head row anchors the tail: silently
 * deleting the newest events leaves the head pointing at a hash the remaining rows can
 * no longer reproduce.
 *
 * <p>Residual risk (documented in THREAT_MODEL.md): an attacker who can rewrite the
 * head row <em>and</em> every event row after the cut can still truncate the tail
 * undetected. Exporting the head hash to an external anchor (log pipeline, another
 * datastore) closes that; the verify endpoint returns the head hash for exactly that
 * purpose.
 */
@Service
public class AuditChainVerifier {

    private static final int PAGE_SIZE = 500;

    private final AuthAuditEventRepository repository;
    private final AuditChainHeadRepository chainHeadRepository;

    public AuditChainVerifier(AuthAuditEventRepository repository,
            AuditChainHeadRepository chainHeadRepository) {
        this.repository = repository;
        this.chainHeadRepository = chainHeadRepository;
    }

    @Transactional(readOnly = true)
    public ChainVerificationResult verify() {
        var headRow = chainHeadRepository.findById(AuditChainHead.SINGLETON_ID);
        String headHash = headRow.map(AuditChainHead::getLastHash).orElse(AuditHashChain.GENESIS_HASH);
        Long headEventId = headRow.map(AuditChainHead::getLastEventId).orElse(null);

        long verified = 0;
        long legacy = 0;
        boolean chainStarted = false;
        String expectedPrevHash = AuditHashChain.GENESIS_HASH;
        Long lastChainedId = null;
        long afterId = 0;

        List<AuthAuditEvent> page;
        do {
            page = repository.findByIdGreaterThanOrderByIdAsc(afterId, PageRequest.ofSize(PAGE_SIZE));
            for (AuthAuditEvent event : page) {
                afterId = event.getId();
                if (event.getEntryHash() == null) {
                    if (chainStarted) {
                        return ChainVerificationResult.broken(verified, legacy, headHash,
                                event.getId(), "unhashed event inside the chained region");
                    }
                    legacy++;
                    continue;
                }
                chainStarted = true;
                if (!expectedPrevHash.equals(event.getPrevHash())) {
                    return ChainVerificationResult.broken(verified, legacy, headHash, event.getId(),
                            "previous-hash link mismatch (an earlier event was altered or removed)");
                }
                String recomputed = AuditHashChain.entryHash(event.getPrevHash(), event);
                if (!recomputed.equals(event.getEntryHash())) {
                    return ChainVerificationResult.broken(verified, legacy, headHash, event.getId(),
                            "entry hash mismatch (event content was altered)");
                }
                expectedPrevHash = event.getEntryHash();
                lastChainedId = event.getId();
                verified++;
            }
        } while (page.size() == PAGE_SIZE);

        if (!expectedPrevHash.equals(headHash)) {
            return ChainVerificationResult.broken(verified, legacy, headHash, null,
                    "chain head mismatch (events after the last verified one were removed)");
        }
        if (headEventId != null && !headEventId.equals(lastChainedId)) {
            return ChainVerificationResult.broken(verified, legacy, headHash, null,
                    "chain head points at event id " + headEventId
                            + " but the newest chained event is " + lastChainedId);
        }
        return ChainVerificationResult.intact(verified, legacy, headHash);
    }
}
