package com.foodmate.infrastructure.persistence.knowledge;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeMapper {
    @Insert(
            "INSERT INTO knowledge_documents(document_id,title,source_type,status,version,storage_key,created_by,updated_by) VALUES (#{documentId},#{title},'admin_upload','uploaded','1',#{storageKey},#{operatorId},#{operatorId})")
    void insertDocument(
            @Param("documentId") long documentId,
            @Param("title") String title,
            @Param("storageKey") String storageKey,
            @Param("operatorId") long operatorId);

    @Update(
            "UPDATE knowledge_documents SET status=#{status},updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE document_id=#{documentId} AND is_deleted=FALSE")
    int updateStatus(
            @Param("documentId") long documentId,
            @Param("status") String status,
            @Param("operatorId") long operatorId);

    @Insert(
            "INSERT INTO knowledge_import_jobs(job_id,operator_id,idempotency_key,requested_mode,source_type,source_name,source_version,license_notice,trace_id,status) VALUES(#{jobId},#{operatorId},#{idempotencyKey},#{mode},#{sourceType},#{sourceName},#{sourceVersion},#{licenseNotice},#{traceId},'uploaded')")
    void insertImportJob(
            @Param("jobId") long jobId,
            @Param("operatorId") long operatorId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("mode") String mode,
            @Param("sourceType") String sourceType,
            @Param("sourceName") String sourceName,
            @Param("sourceVersion") String sourceVersion,
            @Param("licenseNotice") String licenseNotice,
            @Param("traceId") String traceId);

    @Insert(
            "INSERT INTO knowledge_import_items(item_id,job_id,document_id,filename,content_type,file_size,upload_status,index_status) VALUES(#{itemId},#{jobId},#{documentId},#{filename},#{contentType},#{size},'uploaded','pending')")
    void insertImportItem(
            @Param("itemId") long itemId,
            @Param("jobId") long jobId,
            @Param("documentId") long documentId,
            @Param("filename") String filename,
            @Param("contentType") String contentType,
            @Param("size") long size);

    @Insert(
            "INSERT INTO knowledge_index_outbox(outbox_id,item_id,topic,payload_json) VALUES(#{outboxId},#{itemId},'foodmate-knowledge-index-v1',CAST(#{payload} AS jsonb))")
    void insertIndexOutbox(
            @Param("outboxId") long outboxId,
            @Param("itemId") long itemId,
            @Param("payload") String payload);
}
