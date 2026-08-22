package com.foodmate.application.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeService;
import com.foodmate.application.knowledge.service.impl.KnowledgeServiceImpl;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
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
        when(ids.nextId()).thenReturn(42L);
        KnowledgeServiceImpl service =
                new KnowledgeServiceImpl(
                        provider(repository), provider(storage), provider(ids), "foodmate-private");
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
        verify(repository).insertAudit(any(KnowledgeRepository.Audit.class));
    }

    @Test
    void uploadFailureDoesNotPersistDocument() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        when(ids.nextId()).thenReturn(42L);
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storage)
                .put(any(), any(), any(), eq(5L), any());
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

        verifyNoInteractions(repository);
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
        verify(repository, org.mockito.Mockito.never())
                .insertAudit(any(KnowledgeRepository.Audit.class));
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
