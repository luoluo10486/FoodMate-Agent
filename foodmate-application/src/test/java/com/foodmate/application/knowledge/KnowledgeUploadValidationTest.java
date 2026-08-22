package com.foodmate.application.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeService;
import com.foodmate.application.knowledge.service.impl.KnowledgeServiceImpl;
import com.foodmate.shared.id.IdGenerator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class KnowledgeUploadValidationTest {
    @Test
    void batchAcceptsValidatedMarkdownAndNormalizesContentType() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        when(ids.nextId()).thenReturn(100L, 101L, 102L, 103L);
        KnowledgeServiceImpl service = service(repository, storage, ids);

        long batchId =
                service.uploadBatch(
                        7L,
                        new KnowledgeService.ImportBatch(
                                "idem-valid",
                                "admin_upload",
                                "nutrition",
                                "2026-08",
                                "internal",
                                List.of(
                                        new KnowledgeService.ImportFile(
                                                "guide.md",
                                                "text/markdown; charset=utf-8",
                                                4L,
                                                new ByteArrayInputStream("text".getBytes())))),
                        "trace-1");

        assertEquals(100L, batchId);
        verify(storage)
                .put(
                        eq("foodmate-private"),
                        eq("knowledge/public/101/guide.md"),
                        any(InputStream.class),
                        eq(4L),
                        eq("text/markdown"));
        verify(repository)
                .insertImportItem(
                        new KnowledgeRepository.ImportItem(
                                102L, 100L, 101L, "guide.md", "text/markdown", 4L));
    }

    @Test
    void batchRejectsUnauthorizedSourceBeforeCreatingPersistenceFacts() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        KnowledgeServiceImpl service = service(repository, storage, ids);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.uploadBatch(
                                        7L,
                                        new KnowledgeService.ImportBatch(
                                                "idem-source",
                                                "external_import",
                                                "nutrition",
                                                "2026-08",
                                                "internal",
                                                List.of(
                                                        new KnowledgeService.ImportFile(
                                                                "guide.md",
                                                                "text/markdown",
                                                                4L,
                                                                new ByteArrayInputStream(
                                                                        "text".getBytes())))),
                                        "trace-1"));

        assertEquals("KNOWLEDGE_SOURCE_UNAUTHORIZED", exception.getMessage());
        verify(ids, org.mockito.Mockito.never()).nextId();
        verify(storage, org.mockito.Mockito.never())
                .put(any(), any(), any(), anyLong(), any());
        verify(repository, org.mockito.Mockito.never())
                .insertImportJob(any(KnowledgeRepository.ImportJob.class));
        verify(repository, org.mockito.Mockito.never())
                .insertImportItem(any(KnowledgeRepository.ImportItem.class));
        verify(repository, org.mockito.Mockito.never())
                .insertIndexOutbox(anyLong(), anyLong(), anyString());
    }

    @Test
    void batchRejectsForgedMimeAndBasicPiiBeforeObjectStorage() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        ObjectStoragePort storage = mock(ObjectStoragePort.class);
        IdGenerator ids = mock(IdGenerator.class);
        KnowledgeServiceImpl service = service(repository, storage, ids);

        IllegalArgumentException mime =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.uploadBatch(
                                        7L,
                                        new KnowledgeService.ImportBatch(
                                                "idem-mime",
                                                "admin_upload",
                                                "nutrition",
                                                "2026-08",
                                                "internal",
                                                List.of(
                                                        new KnowledgeService.ImportFile(
                                                                "guide.pdf",
                                                                "text/plain",
                                                                4L,
                                                                new ByteArrayInputStream(
                                                                        "text".getBytes())))),
                                        "trace-1"));
        assertEquals("KNOWLEDGE_MIME_INVALID", mime.getMessage());

        IllegalArgumentException pii =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.uploadBatch(
                                        7L,
                                        new KnowledgeService.ImportBatch(
                                                "idem-pii",
                                                "admin_upload",
                                                "nutrition",
                                                "2026-08",
                                                "internal",
                                                List.of(
                                                        new KnowledgeService.ImportFile(
                                                                "guide.md",
                                                                "text/markdown",
                                                                17L,
                                                                new ByteArrayInputStream(
                                                                        "alice@example.com"
                                                                                .getBytes())))),
                                        "trace-1"));
        assertEquals("KNOWLEDGE_PII_DETECTED", pii.getMessage());
        verifyNoInteractions(storage, ids);
        verify(repository, org.mockito.Mockito.never())
                .insertImportJob(any(KnowledgeRepository.ImportJob.class));
    }

    private KnowledgeServiceImpl service(
            KnowledgeRepository repository, ObjectStoragePort storage, IdGenerator ids) {
        return new KnowledgeServiceImpl(
                provider(repository), provider(storage), provider(ids), "foodmate-private");
    }

    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
