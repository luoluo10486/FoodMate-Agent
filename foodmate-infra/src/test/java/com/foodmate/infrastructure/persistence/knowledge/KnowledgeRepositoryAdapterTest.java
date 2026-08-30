package com.foodmate.infrastructure.persistence.knowledge;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.infrastructure.persistence.knowledge.adapter.KnowledgeRepositoryAdapter;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeRepositoryAdapterTest {
    private final KnowledgeMapper mapper = org.mockito.Mockito.mock(KnowledgeMapper.class);
    private final IdGenerator ids = org.mockito.Mockito.mock(IdGenerator.class);
    private final KnowledgeRepositoryAdapter adapter = new KnowledgeRepositoryAdapter(mapper, ids);

    @Test
    void indexedResultUpdatesAuthorityAndCreatesOneProgressEvent() {
        when(mapper.resultMatchesItem(11L, 12L, "v1")).thenReturn(1);
        when(mapper.resultPayloadHash(11L, "v1", 2)).thenReturn(null);
        when(mapper.insertResultInbox(11L, "v1", 2, "sha256:ok")).thenReturn(1);
        when(mapper.markItemIndexed(11L, 12L, 2, 4, "v1", 100L, new BigDecimal("0.12"), "stub-v1", null))
                .thenReturn(1);
        when(mapper.jobIdForItem(11L)).thenReturn(77L);
        when(mapper.job(77L))
                .thenReturn(new KnowledgeRepository.JobView(77L, "completed", 1, 1, 0));
        when(ids.nextId()).thenReturn(900L, 901L);

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
        verify(mapper)
                .insertJobEvent(eq(900L), eq(77L), eq(11L), eq("knowledge.index.indexed"), any());
        verify(mapper)
                .insertJobEvent(eq(901L), eq(77L), eq(11L), eq("knowledge.batch.progress"), any());
    }

    @Test
    void indexedResultReplacesChunkFactsBeforeMarkingDocumentIndexed() {
        when(mapper.resultMatchesItem(11L, 12L, "v1")).thenReturn(1);
        when(mapper.resultPayloadHash(11L, "v1", 1)).thenReturn(null);
        when(mapper.insertResultInbox(11L, "v1", 1, "sha256:chunks")).thenReturn(1);
        when(mapper.markItemIndexed(11L, 12L, 1, 1, "v1", 3L, BigDecimal.ZERO, "stub-v1", null))
                .thenReturn(1);
        when(mapper.jobIdForItem(11L)).thenReturn(77L);
        when(mapper.job(77L))
                .thenReturn(new KnowledgeRepository.JobView(77L, "completed", 1, 1, 0));
        when(ids.nextId()).thenReturn(700L, 701L, 702L);

        adapter.applyIndexResult(
                new KnowledgeRepository.IndexResult(
                        11L,
                        12L,
                        "v1",
                        "indexed",
                        1,
                        null,
                        1,
                        3L,
                        BigDecimal.ZERO,
                        "stub-v1",
                        List.of(
                                new KnowledgeRepository.IndexChunk(
                                        0, "emb-1", "Guide", "Protein guide"))),
                "sha256:chunks");

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(mapper);
        inOrder.verify(mapper)
                .markItemIndexed(11L, 12L, 1, 1, "v1", 3L, BigDecimal.ZERO, "stub-v1", null);
        inOrder.verify(mapper).softDeleteVersionChunks(12L, "v1");
        inOrder.verify(mapper).insertKnowledgeChunks(eq(12L), eq("v1"), any());
        verify(mapper).markDocumentIndexed(12L, "v1");
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
                        anyLong(), anyLong(), anyInt(), anyInt(), any(), anyLong(), any(), any(), any());
        verify(mapper, never()).insertJobEvent(anyLong(), anyLong(), any(), any(), any());
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

    @Test
    void manualRetryResetsAuthorityAndEmitsReplayableProgressFacts() {
        when(mapper.resetItem(11L, 77L)).thenReturn(1);
        when(mapper.job(77L)).thenReturn(new KnowledgeRepository.JobView(77L, "indexing", 1, 0, 0));
        when(ids.nextId()).thenReturn(901L, 902L);

        org.junit.jupiter.api.Assertions.assertEquals(
                1, adapter.retryItem(11L, 77L, 7L, 903L, "{}"));

        verify(mapper).deleteResultInbox(11L);
        verify(mapper).insertIndexOutbox(903L, 11L, "{}");
        verify(mapper).refreshJob(11L);
        verify(mapper)
                .insertJobEvent(eq(901L), eq(77L), eq(11L), eq("knowledge.index.retry"), any());
        verify(mapper)
                .insertJobEvent(eq(902L), eq(77L), eq(11L), eq("knowledge.batch.progress"), any());
    }

    @Test
    void failedIndexRetriesTwiceThenStopsAtThirdAttempt() {
        when(mapper.resultMatchesItem(11L, 12L, "v1")).thenReturn(1);
        when(mapper.resultPayloadHash(eq(11L), eq("v1"), anyInt())).thenReturn(null);
        when(mapper.jobIdForItem(11L)).thenReturn(77L);
        when(mapper.job(77L)).thenReturn(new KnowledgeRepository.JobView(77L, "indexing", 1, 0, 0));
        when(ids.nextId()).thenReturn(901L, 902L, 903L, 904L, 905L, 906L);
        when(mapper.insertResultInbox(anyLong(), any(), anyInt(), any())).thenReturn(1);
        when(mapper.markItemFailed(
                        11L, 12L, "RAG_PARSE_FAILED", "parser rejected the document", 1, "v1"))
                .thenReturn(1);
        when(mapper.markItemFailed(
                        11L, 12L, "RAG_PARSE_FAILED", "parser rejected the document", 2, "v1"))
                .thenReturn(1);
        when(mapper.markItemFailed(
                        11L, 12L, "RAG_PARSE_FAILED", "parser rejected the document", 3, "v1"))
                .thenReturn(1);
        when(mapper.requeueIndexOutbox(11L, 2, 1, "RAG_PARSE_FAILED")).thenReturn(1);
        when(mapper.requeueIndexOutbox(11L, 3, 2, "RAG_PARSE_FAILED")).thenReturn(1);

        for (int attempt = 1; attempt <= 3; attempt++) {
            adapter.applyIndexResult(
                    new KnowledgeRepository.IndexResult(
                            11L,
                            12L,
                            "v1",
                            "index_failed",
                            0,
                            "RAG_PARSE_FAILED",
                            "parser rejected the document",
                            attempt,
                            0L,
                            BigDecimal.ZERO,
                            "stub-v1",
                            List.of()),
                    "sha256:attempt-" + attempt);
        }

        verify(mapper).requeueIndexOutbox(11L, 2, 1, "RAG_PARSE_FAILED");
        verify(mapper).requeueIndexOutbox(11L, 3, 2, "RAG_PARSE_FAILED");
        verify(mapper, never()).requeueIndexOutbox(11L, 4, 0, "RAG_PARSE_FAILED");
    }
}
