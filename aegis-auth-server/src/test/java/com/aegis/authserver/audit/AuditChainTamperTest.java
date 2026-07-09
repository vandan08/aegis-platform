package com.aegis.authserver.audit;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives {@link AuditService} + {@link AuditChainVerifier} against an in-memory "table"
 * and then attacks it the way someone with direct database access would: rewriting a
 * row, deleting a row mid-chain, truncating the tail, injecting an unhashed row.
 * Every attack must be detected. Field mutation via reflection stands in for SQL
 * UPDATE — the entity is deliberately immutable through its API.
 */
class AuditChainTamperTest {

    private final List<AuthAuditEvent> table = new ArrayList<>();
    private AuditChainHead head;
    private AuditService auditService;
    private AuditChainVerifier verifier;
    private long idSequence = 0;

    @BeforeEach
    void setUpInMemoryDatabase() {
        head = AuditChainHead.genesis();

        AuthAuditEventRepository events = mock(AuthAuditEventRepository.class);
        when(events.save(any())).thenAnswer(invocation -> {
            AuthAuditEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", ++idSequence);
            table.add(event);
            return event;
        });
        when(events.findByIdGreaterThanOrderByIdAsc(anyLong(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    long afterId = invocation.getArgument(0);
                    int pageSize = invocation.<Pageable>getArgument(1).getPageSize();
                    return table.stream()
                            .filter(e -> e.getId() > afterId)
                            .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                            .limit(pageSize)
                            .toList();
                });

        AuditChainHeadRepository heads = mock(AuditChainHeadRepository.class);
        when(heads.lockHead()).thenAnswer(invocation -> java.util.Optional.of(head));
        when(heads.findById(any())).thenAnswer(invocation -> java.util.Optional.of(head));
        when(heads.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        auditService = new AuditService(events, heads);
        verifier = new AuditChainVerifier(events, heads);
    }

    private void recordThreeEvents() {
        auditService.record(AuditEventType.LOGIN_SUCCESS, "alice", "web login", "10.0.0.1");
        auditService.record(AuditEventType.LOGIN_FAILURE, "mallory", "bad password", "10.6.6.6");
        auditService.record(AuditEventType.KEY_ROTATED, "admin", "new kid=k2", null);
    }

    @Test
    @DisplayName("each event links to its predecessor and the head tracks the tip")
    void appendsFormALinkedChain() {
        recordThreeEvents();

        assertThat(table.get(0).getPrevHash()).isEqualTo(AuditHashChain.GENESIS_HASH);
        assertThat(table.get(1).getPrevHash()).isEqualTo(table.get(0).getEntryHash());
        assertThat(table.get(2).getPrevHash()).isEqualTo(table.get(1).getEntryHash());
        assertThat(head.getLastHash()).isEqualTo(table.get(2).getEntryHash());
        assertThat(head.getLastEventId()).isEqualTo(table.get(2).getId());
    }

    @Test
    @DisplayName("an untouched chain verifies as intact")
    void intactChainVerifies() {
        recordThreeEvents();

        ChainVerificationResult result = verifier.verify();

        assertThat(result.intact()).isTrue();
        assertThat(result.verifiedEvents()).isEqualTo(3);
        assertThat(result.legacyEvents()).isZero();
        assertThat(result.headHash()).isEqualTo(head.getLastHash());
    }

    @Test
    @DisplayName("rewriting a row's content (SQL UPDATE) is detected at that row")
    void rewrittenRowIsDetected() {
        recordThreeEvents();
        ReflectionTestUtils.setField(table.get(1), "detail", "login success"); // cover the tracks

        ChainVerificationResult result = verifier.verify();

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenEventId()).isEqualTo(table.get(1).getId());
        assertThat(result.reason()).contains("content was altered");
    }

    @Test
    @DisplayName("deleting a row mid-chain (SQL DELETE) breaks the link at the next row")
    void deletedRowIsDetected() {
        recordThreeEvents();
        AuthAuditEvent removed = table.remove(1);

        ChainVerificationResult result = verifier.verify();

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenEventId()).isEqualTo(removed.getId() + 1);
        assertThat(result.reason()).contains("previous-hash link mismatch");
    }

    @Test
    @DisplayName("truncating the tail is detected via the chain-head anchor")
    void truncatedTailIsDetected() {
        recordThreeEvents();
        table.remove(2); // delete the newest event but leave the head row alone

        ChainVerificationResult result = verifier.verify();

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenEventId()).isNull();
        assertThat(result.reason()).contains("chain head mismatch");
    }

    @Test
    @DisplayName("injecting an unhashed row into the chained region is detected")
    void injectedUnhashedRowIsDetected() {
        recordThreeEvents();
        AuthAuditEvent forged = new AuthAuditEvent("LOGIN_SUCCESS", "mallory", "looks fine", null);
        ReflectionTestUtils.setField(forged, "id", ++idSequence);
        table.add(forged); // INSERT without hashes, hoping it passes as legacy

        ChainVerificationResult result = verifier.verify();

        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenEventId()).isEqualTo(forged.getId());
        assertThat(result.reason()).contains("unhashed event inside the chained region");
    }

    @Test
    @DisplayName("rows that predate the chain (pre-V6) count as legacy, not as breaks")
    void legacyRowsAreTolerated() {
        AuthAuditEvent legacy = new AuthAuditEvent("LOGIN_SUCCESS", "old-user", "pre-V6 row", null);
        ReflectionTestUtils.setField(legacy, "id", ++idSequence);
        table.add(legacy);
        recordThreeEvents();

        ChainVerificationResult result = verifier.verify();

        assertThat(result.intact()).isTrue();
        assertThat(result.verifiedEvents()).isEqualTo(3);
        assertThat(result.legacyEvents()).isEqualTo(1);
    }
}
