package com.foodmate.infrastructure.persistence.retention;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Fixed SQL for the two resource types governed by the retention policy. */
@Mapper
public interface DataRetentionDatabasePurgeMapper {
    @Delete(
            "DELETE FROM knowledge_index_result_inbox WHERE item_id IN (SELECT item_id FROM knowledge_import_items WHERE document_id=#{documentId})")
    int deleteKnowledgeIndexResults(@Param("documentId") long documentId);

    @Delete(
            "DELETE FROM knowledge_index_outbox WHERE item_id IN (SELECT item_id FROM knowledge_import_items WHERE document_id=#{documentId})")
    int deleteKnowledgeIndexOutbox(@Param("documentId") long documentId);

    @Delete(
            "DELETE FROM knowledge_import_sse_outbox WHERE item_id IN (SELECT item_id FROM knowledge_import_items WHERE document_id=#{documentId})")
    int deleteKnowledgeImportEvents(@Param("documentId") long documentId);

    @Delete("DELETE FROM knowledge_chunks WHERE document_id=#{documentId}")
    int deleteKnowledgeChunks(@Param("documentId") long documentId);

    @Delete("DELETE FROM knowledge_visibility_outbox WHERE document_id=#{documentId}")
    int deleteKnowledgeVisibilityOutbox(@Param("documentId") long documentId);

    @Delete("DELETE FROM knowledge_import_items WHERE document_id=#{documentId}")
    int deleteKnowledgeImportItems(@Param("documentId") long documentId);

    @Delete("DELETE FROM knowledge_documents WHERE document_id=#{documentId} AND is_deleted=TRUE")
    int deleteKnowledgeDocument(@Param("documentId") long documentId);

    @Delete("DELETE FROM admin_export_jobs WHERE admin_export_job_id=#{jobId} AND is_deleted=TRUE")
    int deleteAdminExportJob(@Param("jobId") long jobId);
}
