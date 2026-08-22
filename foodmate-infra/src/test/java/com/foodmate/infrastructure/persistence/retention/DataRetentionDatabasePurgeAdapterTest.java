package com.foodmate.infrastructure.persistence.retention;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.foodmate.infrastructure.persistence.retention.adapter.DataRetentionDatabasePurgeAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class DataRetentionDatabasePurgeAdapterTest {
    @Test
    void knowledgeDocumentChildrenAreRemovedBeforeTheSoftDeletedDocument() {
        DataRetentionDatabasePurgeMapper mapper =
                Mockito.mock(DataRetentionDatabasePurgeMapper.class);
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
    void exportJobPurgeIsBoundToSoftDeletedRows() {
        DataRetentionDatabasePurgeMapper mapper =
                Mockito.mock(DataRetentionDatabasePurgeMapper.class);
        DataRetentionDatabasePurgeAdapter adapter = new DataRetentionDatabasePurgeAdapter(mapper);

        adapter.purge("admin_export_job", 77L);

        verify(mapper).deleteAdminExportJob(77L);
    }

    @Test
    void unsupportedResourceTypeFailsClosed() {
        DataRetentionDatabasePurgeAdapter adapter =
                new DataRetentionDatabasePurgeAdapter(
                        Mockito.mock(DataRetentionDatabasePurgeMapper.class));

        assertThrows(IllegalArgumentException.class, () -> adapter.purge("users", 1L));
    }
}
