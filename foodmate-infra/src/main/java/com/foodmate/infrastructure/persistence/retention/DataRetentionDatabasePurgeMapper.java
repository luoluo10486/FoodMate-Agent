package com.foodmate.infrastructure.persistence.retention;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Fixed SQL for the two resource types governed by the retention policy. */
@Mapper
public interface DataRetentionDatabasePurgeMapper {
    @Select(
            "SELECT CASE WHEN EXISTS (SELECT 1 FROM data_purge_requests r JOIN data_retention_policies p ON p.policy_id=r.policy_id JOIN knowledge_documents d ON d.document_id=#{documentId} WHERE r.resource_type='knowledge_document' AND r.resource_id=#{documentId} AND r.status IN ('approved','running') AND p.status='active' AND p.hard_delete_enabled=TRUE AND d.is_deleted=TRUE AND NOT EXISTS (SELECT 1 FROM data_legal_holds h WHERE h.resource_type='knowledge_document' AND h.resource_id=#{documentId} AND h.status='active')) THEN 1 ELSE 0 END")
    int knowledgeDocumentPurgeAllowed(@Param("documentId") long documentId);

    @Select(
            "SELECT CASE WHEN EXISTS (SELECT 1 FROM data_purge_requests r JOIN data_retention_policies p ON p.policy_id=r.policy_id JOIN admin_export_jobs j ON j.admin_export_job_id=#{jobId} WHERE r.resource_type='admin_export_job' AND r.resource_id=#{jobId} AND r.status IN ('approved','running') AND p.status='active' AND p.hard_delete_enabled=TRUE AND j.is_deleted=TRUE AND NOT EXISTS (SELECT 1 FROM data_legal_holds h WHERE h.resource_type='admin_export_job' AND h.resource_id=#{jobId} AND h.status='active')) THEN 1 ELSE 0 END")
    int adminExportJobPurgeAllowed(@Param("jobId") long jobId);

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
