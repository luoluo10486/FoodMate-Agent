package com.foodmate.infrastructure.persistence.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.foodmate.application.retention.port.out.DataRetentionDatabasePurgePort.PurgeResult;
import com.foodmate.infrastructure.persistence.retention.adapter.DataRetentionDatabasePurgeAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class DataRetentionDatabasePurgeAdapterTest {
    @Test
    void knowledgeDocumentChildrenAreRemovedBeforeTheSoftDeletedDocument() {
        DataRetentionDatabasePurgeMapper mapper =
                Mockito.mock(DataRetentionDatabasePurgeMapper.class);
        Mockito.when(mapper.knowledgeDocumentPurgeAllowed(42L)).thenReturn(1);
        DataRetentionDatabasePurgeAdapter adapter = new DataRetentionDatabasePurgeAdapter(mapper);

        adapter.purge("knowledge_document", 42L);

        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteKnowledgeIndexResults(42L);
        order.verify(mapper).deleteKnowledgeIndexOutbox(42L);
        order.verify(mapper).deleteKnowledgeImportEvents(42L);
        order.verify(mapper).deleteKnowledgeChunks(42L);
        order.verify(mapper).deleteKnowledgeVisibilityOutbox(42L);
        order.verify(mapper).deleteKnowledgeImportItems(42L);
        order.verify(mapper).deleteKnowledgeDocument(42L);
    }

    @Test
    void knowledgeDocumentResultContainsDeletedCountAndAbsenceVerification() {
        DataRetentionDatabasePurgeMapper mapper =
                Mockito.mock(DataRetentionDatabasePurgeMapper.class);
        Mockito.when(mapper.knowledgeDocumentPurgeAllowed(42L)).thenReturn(1);
        Mockito.when(mapper.deleteKnowledgeIndexResults(42L)).thenReturn(2);
        Mockito.when(mapper.deleteKnowledgeIndexOutbox(42L)).thenReturn(1);
        Mockito.when(mapper.deleteKnowledgeImportEvents(42L)).thenReturn(3);
        Mockito.when(mapper.deleteKnowledgeChunks(42L)).thenReturn(4);
        Mockito.when(mapper.deleteKnowledgeVisibilityOutbox(42L)).thenReturn(1);
        Mockito.when(mapper.deleteKnowledgeImportItems(42L)).thenReturn(2);
        Mockito.when(mapper.deleteKnowledgeDocument(42L)).thenReturn(1);
        Mockito.when(mapper.knowledgeDocumentExists(42L)).thenReturn(0);
        DataRetentionDatabasePurgeAdapter adapter = new DataRetentionDatabasePurgeAdapter(mapper);

        PurgeResult result = adapter.purgeWithResult("knowledge_document", 42L);

        assertEquals(new PurgeResult("postgresql", 14, true), result);
    }

    @Test
    void exportJobPurgeIsBoundToSoftDeletedRows() {
        DataRetentionDatabasePurgeMapper mapper =
                Mockito.mock(DataRetentionDatabasePurgeMapper.class);
        Mockito.when(mapper.adminExportJobPurgeAllowed(77L)).thenReturn(1);
        DataRetentionDatabasePurgeAdapter adapter = new DataRetentionDatabasePurgeAdapter(mapper);

        adapter.purge("admin_export_job", 77L);

        verify(mapper).deleteAdminExportJob(77L);
    }

    @Test
    void missingExportJobIsStillAValidIdempotentResultWhenGuardAllowsIt() {
        DataRetentionDatabasePurgeMapper mapper =
                Mockito.mock(DataRetentionDatabasePurgeMapper.class);
        Mockito.when(mapper.adminExportJobPurgeAllowed(77L)).thenReturn(1);
        Mockito.when(mapper.adminExportJobExists(77L)).thenReturn(0);
        DataRetentionDatabasePurgeAdapter adapter = new DataRetentionDatabasePurgeAdapter(mapper);

        PurgeResult result = adapter.purgeWithResult("admin_export_job", 77L);

        assertEquals(new PurgeResult("postgresql", 0, true), result);
    }

    @Test
    void unsupportedResourceTypeFailsClosed() {
        DataRetentionDatabasePurgeAdapter adapter =
                new DataRetentionDatabasePurgeAdapter(
                        Mockito.mock(DataRetentionDatabasePurgeMapper.class));

        assertThrows(IllegalArgumentException.class, () -> adapter.purge("users", 1L));
    }

    @Test
    void knowledgeDocumentGuardBlocksDeletionWhenPolicyOrHoldIsUnsafe() {
        DataRetentionDatabasePurgeMapper mapper =
                Mockito.mock(DataRetentionDatabasePurgeMapper.class);
        Mockito.when(mapper.knowledgeDocumentPurgeAllowed(42L)).thenReturn(0);
        DataRetentionDatabasePurgeAdapter adapter = new DataRetentionDatabasePurgeAdapter(mapper);

        assertThrows(IllegalStateException.class, () -> adapter.purge("knowledge_document", 42L));
        Mockito.verify(mapper, Mockito.never()).deleteKnowledgeDocument(42L);
    }
}
