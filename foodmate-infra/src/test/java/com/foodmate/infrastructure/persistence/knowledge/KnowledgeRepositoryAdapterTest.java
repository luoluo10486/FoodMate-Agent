package com.foodmate.infrastructure.persistence.knowledge;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.infrastructure.persistence.knowledge.adapter.KnowledgeRepositoryAdapter;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class KnowledgeRepositoryAdapterTest {
    private final KnowledgeMapper mapper = org.mockito.Mockito.mock(KnowledgeMapper.class);
    private final OperationAuditPort audit = org.mockito.Mockito.mock(OperationAuditPort.class);
    private final IdGenerator ids = org.mockito.Mockito.mock(IdGenerator.class);
    private final KnowledgeRepositoryAdapter adapter =
            new KnowledgeRepositoryAdapter(mapper, audit, ids);

    @Test
    void indexedResultUpdatesAuthorityAndCreatesOneProgressEvent() {
        when(mapper.resultMatchesItem(11L, 12L, "v1")).thenReturn(1);
        when(mapper.resultPayloadHash(11L, "v1", 2)).thenReturn(null);
        when(mapper.insertResultInbox(11L, "v1", 2, "sha256:ok")).thenReturn(1);
        when(mapper.markItemIndexed(11L, 12L, 2, 4, "v1", 100L, new BigDecimal("0.12"), "stub-v1"))
                .thenReturn(1);
        when(ids.nextId()).thenReturn(900L);

        adapter.applyIndexResult(
                new KnowledgeRepository.IndexResult(
                        11L,
                        12L,
                        "v1",
                        "indexed",
                        4,
                        null,
                        2,
                        100L,
                        new BigDecimal("0.12"),
                        "stub-v1"),
                "sha256:ok");

        verify(mapper).markDocumentIndexed(12L, "v1");
        verify(mapper).refreshJob(11L);
        verify(mapper).insertJobEvent(eq(900L), eq(11L), eq("knowledge.index.indexed"), any());
    }

    @Test
    void sameResultHashIsAcknowledgedWithoutRepeatingSideEffects() {
        when(mapper.resultMatchesItem(11L, 12L, "v1")).thenReturn(1);
        when(mapper.resultPayloadHash(11L, "v1", 2)).thenReturn("sha256:ok");

        adapter.applyIndexResult(
                new KnowledgeRepository.IndexResult(
                        11L, 12L, "v1", "indexed", 4, null, 2, 0L, BigDecimal.ZERO, "stub-v1"),
                "sha256:ok");

        verify(mapper, never())
                .markItemIndexed(
                        anyLong(), anyLong(), anyInt(), anyInt(), any(), anyLong(), any(), any());
        verify(mapper, never()).insertJobEvent(anyLong(), anyLong(), any(), any());
    }

    @Test
    void resultForUnknownDocumentVersionIsRejected() {
        when(mapper.resultMatchesItem(11L, 12L, "v2")).thenReturn(0);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        adapter.applyIndexResult(
                                new KnowledgeRepository.IndexResult(
                                        11L,
                                        12L,
                                        "v2",
                                        "indexed",
                                        1,
                                        null,
                                        1,
                                        0L,
                                        BigDecimal.ZERO,
                                        "stub-v1"),
                                "sha256:bad"));
        verify(mapper, never()).insertResultInbox(anyLong(), any(), anyInt(), any());
    }
}
