package com.foodmate.application.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeService;
import com.foodmate.application.knowledge.service.impl.KnowledgeServiceImpl;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import com.foodmate.shared.trace.TraceContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class KnowledgeServiceImplTest {
    @Test
    void sameBatchIdempotencyKeyReturnsExistingJobWithoutUploadingAgain() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        when(repository.findImportJob(7L, "idem-1"))
                .thenReturn(
                        new KnowledgeRepository.ImportJob(
                                99L,
                                7L,
                                "idem-1",
                                "stub",
                                "policy",
                                "nutrition",
                                "2026-08",
                                "internal",
                                "trace-old"));
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository), provider(storage), provider(ids), "foodmate-private");

        long result =
                service.uploadBatch(
                        7L,
                        new KnowledgeService.ImportBatch(
                                "idem-1",
                                "policy",
                                "nutrition",
                                "2026-08",
                                "internal",
                                java.util.List.of(
                                        new KnowledgeService.ImportFile(
                                                "guide.md",
                                                "text/markdown",
                                                4L,
                                                new ByteArrayInputStream("text".getBytes())))),
                        "trace-new");

        assertEquals(99L, result);
        verify(repository).findImportJob(7L, "idem-1");
        verifyNoInteractions(storage, ids);
    }

    @Test
    void reusedIdempotencyKeyWithDifferentSourceIsRejected() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        when(repository.findImportJob(7L, "idem-1"))
                .thenReturn(
                        new KnowledgeRepository.ImportJob(
                                99L,
                                7L,
                                "idem-1",
                                "stub",
                                "policy",
                                "nutrition",
                                "2026-07",
                                "internal",
                                "trace-old"));
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository), provider(storage), provider(ids), "foodmate-private");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.uploadBatch(
                                7L,
                                new KnowledgeService.ImportBatch(
                                        "idem-1",
                                        "policy",
                                        "nutrition",
                                        "2026-08",
                                        "internal",
                                        java.util.List.of(
                                                new KnowledgeService.ImportFile(
                                                        "guide.md",
                                                        "text/markdown",
                                                        4L,
                                                        new ByteArrayInputStream(
                                                                "text".getBytes())))),
                                "trace-new"));
        verifyNoInteractions(storage, ids);
    }

    @Test
    void uploadStoresObjectBeforePersistingDocument() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(ids.nextId()).thenReturn(42L);
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository),
                        provider(storage),
                        provider(ids),
                        "foodmate-private",
                        provider(audit));
        InputStream input = new ByteArrayInputStream("hello".getBytes());

        assertEquals(42L, service.upload(7L, "note.md", "text/markdown", 5L, input, "trace-1"));

        verify(storage).ensureBucket("foodmate-private");
        verify(storage)
                .put(
                        eq("foodmate-private"),
                        eq("knowledge/7/42-note.md"),
                        eq(input),
                        eq(5L),
                        eq("text/markdown"));
        verify(repository).insertDocument(42L, "note.md", "knowledge/7/42-note.md", 7L);
        verify(audit)
                .record(
                        any(TraceContext.class),
                        eq(7L),
                        eq("knowledge_document"),
                        eq("42"),
                        eq("knowledge.upload"),
                        eq("success"),
                        any(),
                        any(),
                        any(),
                        any());
    }

    @Test
    void uploadFailureDoesNotPersistDocument() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(ids.nextId()).thenReturn(42L);
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storage)
                .put(any(), any(), any(), eq(5L), any());
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository),
                        provider(storage),
                        provider(ids),
                        "foodmate-private",
                        provider(audit));

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.upload(
                                7L,
                                "note.md",
                                "text/markdown",
                                5L,
                                new ByteArrayInputStream("hello".getBytes()),
                                "trace-1"));

        verifyNoInteractions(repository);
        verify(audit)
                .recordFailure(
                        any(TraceContext.class),
                        eq(7L),
                        eq("knowledge_document"),
                        eq("42"),
                        eq("knowledge.upload"),
                        eq("failed"),
                        eq("INTERNAL_ERROR"),
                        isNull(),
                        isNull(),
                        any());
    }

    @Test
    void uploadDatabaseFailureCompensatesTheNewObject() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        when(ids.nextId()).thenReturn(42L);
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository)
                .insertDocument(42L, "note.md", "knowledge/7/42-note.md", 7L);
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository), provider(storage), provider(ids), "foodmate-private");

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.upload(
                                7L,
                                "note.md",
                                "text/markdown",
                                5L,
                                new ByteArrayInputStream("hello".getBytes()),
                                "trace-1"));

        verify(storage).delete("foodmate-private", "knowledge/7/42-note.md");
    }

    @Test
    void uploadBatchDatabaseFailureIsRecordedAsFailedAudit() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(ids.nextId()).thenReturn(10L, 11L, 12L, 13L);
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository)
                .insertDocument(11L, "guide.md", "knowledge/public/11/guide.md", 7L);
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository),
                        provider(storage),
                        provider(ids),
                        "foodmate-private",
                        provider(audit));

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.uploadBatch(
                                7L,
                                new KnowledgeService.ImportBatch(
                                        "batch-1",
                                        "policy",
                                        "nutrition",
                                        "2026-08",
                                        "internal",
                                        java.util.List.of(
                                                new KnowledgeService.ImportFile(
                                                        "guide.md",
                                                        "text/markdown",
                                                        5L,
                                                        new ByteArrayInputStream(
                                                                "hello".getBytes())))),
                                "trace-batch"));

        verify(storage).delete("foodmate-private", "knowledge/public/11/guide.md");
        verify(audit)
                .recordFailure(
                        any(TraceContext.class),
                        eq(7L),
                        eq("knowledge_document"),
                        eq("10"),
                        eq("knowledge.import_batch.create"),
                        eq("failed"),
                        eq("INTERNAL_ERROR"),
                        isNull(),
                        isNull(),
                        any());
    }

    @Test
    void updateStatusRequiresExistingDocument() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        when(repository.updateStatus(42L, KnowledgeDocumentStatus.INDEXED, 7L)).thenReturn(0);
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository), provider(storage), provider(ids), "foodmate-private");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus(42L, KnowledgeDocumentStatus.INDEXED, 7L, "trace-1"));

        verify(repository).updateStatus(42L, KnowledgeDocumentStatus.INDEXED, 7L);
    }

    @Test
    void updateStatusFailureIsRecordedAsFailedAudit() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(repository.updateStatus(42L, KnowledgeDocumentStatus.INDEXED, 7L)).thenReturn(0);
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository),
                        provider(storage),
                        provider(ids),
                        "foodmate-private",
                        provider(audit));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus(42L, KnowledgeDocumentStatus.INDEXED, 7L, "trace-1"));

        verify(audit)
                .recordFailure(
                        any(TraceContext.class),
                        eq(7L),
                        eq("knowledge_document"),
                        eq("42"),
                        eq("knowledge.status.update"),
                        eq("failed"),
                        eq("INVALID_ARGUMENT"),
                        isNull(),
                        isNull(),
                        any());
    }

    @Test
    void invalidVisibilityIsRecordedAsFailedAudit() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository),
                        provider(storage),
                        provider(ids),
                        "foodmate-private",
                        provider(audit));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.changeVisibility(42L, "invalid", 7L, "trace-1"));

        verify(audit)
                .recordFailure(
                        any(TraceContext.class),
                        eq(7L),
                        eq("knowledge_document"),
                        eq("42"),
                        eq("knowledge.visibility.invalid"),
                        eq("failed"),
                        eq("INVALID_ARGUMENT"),
                        isNull(),
                        isNull(),
                        any());
    }

    @Test
    void retryFailureIsRecordedAsFailedAudit() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(repository.job(77L))
                .thenReturn(new KnowledgeRepository.JobView(77L, "failed", 1, 0, 1));
        when(repository.jobItems(77L))
                .thenReturn(
                        java.util.List.of(
                                new KnowledgeRepository.ItemView(
                                        88L,
                                        42L,
                                        "guide.md",
                                        "uploaded",
                                        "index_failed",
                                        3,
                                        "RAG_FAILED")));
        when(ids.nextId()).thenReturn(99L);
        when(repository.retryItem(88L, 77L, 7L, 99L, "{}")).thenReturn(0);
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository),
                        provider(storage),
                        provider(ids),
                        "foodmate-private",
                        provider(audit));

        assertThrows(
                IllegalArgumentException.class, () -> service.retryItem(77L, 42L, 7L, "trace-1"));

        verify(audit)
                .recordFailure(
                        any(TraceContext.class),
                        eq(7L),
                        eq("knowledge_document"),
                        eq("42"),
                        eq("knowledge.import_item.retry"),
                        eq("failed"),
                        eq("INVALID_ARGUMENT"),
                        isNull(),
                        isNull(),
                        any());
    }

    @Test
    void visibilityOutboxCarriesAuthoritativeDocumentVersionState() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        when(repository.document(42L))
                .thenReturn(new KnowledgeRepository.DocumentView(42L, "v-old", false));
        when(repository.updateVisibility(42L, "disabled", 7L)).thenReturn(1);
        when(ids.nextId()).thenReturn(99L);
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository), provider(storage), provider(ids), "foodmate-private");

        service.changeVisibility(42L, "disabled", 7L, "trace-1");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(repository).insertVisibilityOutbox(eq(99L), eq(42L), payload.capture());
        assertFalse(payload.getValue().contains("\"current_version\":true"));
        org.junit.jupiter.api.Assertions.assertTrue(
                payload.getValue().contains("\"version\":\"v-old\""));
        org.junit.jupiter.api.Assertions.assertTrue(
                payload.getValue().contains("\"current_version\":false"));
    }

    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
