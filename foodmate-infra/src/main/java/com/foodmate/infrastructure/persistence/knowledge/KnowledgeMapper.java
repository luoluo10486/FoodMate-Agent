package com.foodmate.infrastructure.persistence.knowledge;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
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
            "UPDATE knowledge_documents SET source_type=#{sourceType},source_name=#{sourceName},source_version=#{sourceVersion},license_notice=#{licenseNotice},version=#{sourceVersion},updated_by=#{operatorId},updated_at=CURRENT_TIMESTAMP WHERE document_id=#{documentId}")
    void updateDocumentSource(
            @Param("documentId") long documentId,
            @Param("sourceType") String sourceType,
            @Param("sourceName") String sourceName,
            @Param("sourceVersion") String sourceVersion,
            @Param("licenseNotice") String licenseNotice,
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

    @Update(
            "UPDATE knowledge_documents SET visibility=#{visibility},is_deleted=(#{visibility}='deleted'),updated_by=#{operatorId},updated_at=CURRENT_TIMESTAMP WHERE document_id=#{documentId} AND (#{visibility}<>'published' OR (status='indexed' AND is_deleted=FALSE AND current_version=TRUE))")
    int updateVisibility(
            @Param("documentId") long documentId,
            @Param("visibility") String visibility,
            @Param("operatorId") long operatorId);

    @Insert(
            "INSERT INTO knowledge_visibility_outbox(outbox_id,document_id,topic,payload_json) VALUES(#{outboxId},#{documentId},'foodmate-knowledge-visibility-v1',CAST(#{payload} AS jsonb))")
    void insertVisibilityOutbox(
            @Param("outboxId") long outboxId,
            @Param("documentId") long documentId,
            @Param("payload") String payload);

    @Select(
            "SELECT outbox_id AS outboxId,item_id AS itemOrDocumentId,topic,payload_json::text AS payload FROM knowledge_index_outbox WHERE status='pending' AND available_at<=CURRENT_TIMESTAMP ORDER BY outbox_id LIMIT #{limit}")
    List<KnowledgeRepository.OutboxRow> pendingIndexOutbox(int limit);

    @Select(
            "SELECT outbox_id AS outboxId,document_id AS itemOrDocumentId,topic,payload_json::text AS payload FROM knowledge_visibility_outbox WHERE status='pending' AND available_at<=CURRENT_TIMESTAMP ORDER BY outbox_id LIMIT #{limit}")
    List<KnowledgeRepository.OutboxRow> pendingVisibilityOutbox(int limit);

    @Update(
            "UPDATE knowledge_index_outbox SET owner_token=#{owner},lease_until=CURRENT_TIMESTAMP+INTERVAL '30 seconds',attempt_count=attempt_count+1,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=#{outboxId} AND status='pending' AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)")
    int leaseIndexOutbox(@Param("outboxId") long outboxId, @Param("owner") String owner);

    @Update(
            "UPDATE knowledge_visibility_outbox SET owner_token=#{owner},lease_until=CURRENT_TIMESTAMP+INTERVAL '30 seconds',attempt_count=attempt_count+1,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=#{outboxId} AND status='pending' AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)")
    int leaseVisibilityOutbox(@Param("outboxId") long outboxId, @Param("owner") String owner);

    @Update(
            "UPDATE knowledge_index_outbox SET status='published',owner_token=NULL,lease_until=NULL,published_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=#{outboxId}")
    void markIndexOutboxPublished(long outboxId);

    @Update(
            "UPDATE knowledge_visibility_outbox SET status='published',owner_token=NULL,lease_until=NULL,published_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=#{outboxId}")
    void markVisibilityOutboxPublished(long outboxId);

    @Update(
            "UPDATE knowledge_index_outbox SET owner_token=NULL,lease_until=NULL,last_error=#{error},available_at=CURRENT_TIMESTAMP+INTERVAL '2 seconds',updated_at=CURRENT_TIMESTAMP WHERE outbox_id=#{outboxId}")
    void retryIndexOutbox(@Param("outboxId") long outboxId, @Param("error") String error);

    @Update(
            "UPDATE knowledge_visibility_outbox SET owner_token=NULL,lease_until=NULL,last_error=#{error},available_at=CURRENT_TIMESTAMP+INTERVAL '2 seconds',updated_at=CURRENT_TIMESTAMP WHERE outbox_id=#{outboxId}")
    void retryVisibilityOutbox(@Param("outboxId") long outboxId, @Param("error") String error);

    @Insert(
            "INSERT INTO knowledge_index_result_inbox(item_id,document_version,attempt_count,payload_hash) VALUES(#{itemId},#{version},#{attempt},#{payloadHash}) ON CONFLICT (item_id,document_version,attempt_count) DO NOTHING")
    int insertResultInbox(
            @Param("itemId") long itemId,
            @Param("version") String version,
            @Param("attempt") int attempt,
            @Param("payloadHash") String payloadHash);

    @Update(
            "UPDATE knowledge_import_items SET index_status='indexed',attempt_count=GREATEST(attempt_count,#{attempt}),chunk_count=#{chunkCount},indexed_at=CURRENT_TIMESTAMP,error_code=NULL,error_summary=NULL,updated_at=CURRENT_TIMESTAMP WHERE item_id=#{itemId} AND document_id=#{documentId} AND index_status<>'indexed'")
    int markItemIndexed(
            @Param("itemId") long itemId,
            @Param("documentId") long documentId,
            @Param("attempt") int attempt,
            @Param("chunkCount") int chunkCount);

    @Update(
            "UPDATE knowledge_documents SET status='indexed',indexed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE document_id=#{documentId} AND is_deleted=FALSE")
    void markDocumentIndexed(long documentId);

    @Update(
            "UPDATE knowledge_import_items SET index_status=CASE WHEN #{attempt}>=3 THEN 'index_failed' ELSE 'pending' END,attempt_count=GREATEST(attempt_count,LEAST(3,#{attempt})),error_code=#{errorCode},error_summary=#{errorCode},updated_at=CURRENT_TIMESTAMP WHERE item_id=#{itemId} AND document_id=#{documentId} AND index_status<>'indexed'")
    void markItemFailed(
            @Param("itemId") long itemId,
            @Param("documentId") long documentId,
            @Param("errorCode") String errorCode,
            @Param("attempt") int attempt);

    @Update(
            "UPDATE knowledge_index_outbox SET status='pending',attempt_count=0,available_at=CURRENT_TIMESTAMP + (#{delaySeconds} * INTERVAL '1 second'),owner_token=NULL,lease_until=NULL,last_error=#{errorCode},payload_json=jsonb_set(payload_json,'{attempt}',to_jsonb(#{attempt}::int),true),updated_at=CURRENT_TIMESTAMP WHERE item_id=#{itemId} AND topic='foodmate-knowledge-index-v1'")
    int requeueIndexOutbox(
            @Param("itemId") long itemId,
            @Param("attempt") int attempt,
            @Param("delaySeconds") int delaySeconds,
            @Param("errorCode") String errorCode);

    @Update(
            "UPDATE knowledge_import_jobs j SET status=CASE WHEN EXISTS(SELECT 1 FROM knowledge_import_items i WHERE i.job_id=j.job_id AND i.index_status IN ('pending','parsing','parsed','indexing')) THEN 'indexing' WHEN NOT EXISTS(SELECT 1 FROM knowledge_import_items i WHERE i.job_id=j.job_id AND i.index_status='indexed') THEN 'failed' WHEN EXISTS(SELECT 1 FROM knowledge_import_items i WHERE i.job_id=j.job_id AND i.index_status='index_failed') THEN 'partial_failed' ELSE 'completed' END,updated_at=CURRENT_TIMESTAMP,completed_at=CASE WHEN NOT EXISTS(SELECT 1 FROM knowledge_import_items i WHERE i.job_id=j.job_id AND i.index_status IN ('pending','parsing','parsed','indexing')) THEN CURRENT_TIMESTAMP ELSE NULL END WHERE j.job_id=(SELECT job_id FROM knowledge_import_items WHERE item_id=#{itemId})")
    void refreshJob(long itemId);

    @Insert(
            "INSERT INTO knowledge_import_sse_outbox(event_id,job_id,item_id,event_type,payload_json) SELECT #{eventId},job_id,item_id,#{eventType},CAST(#{payload} AS jsonb) FROM knowledge_import_items WHERE item_id=#{itemId}")
    void insertJobEvent(
            @Param("eventId") long eventId,
            @Param("itemId") long itemId,
            @Param("eventType") String eventType,
            @Param("payload") String payload);

    @Select(
            "SELECT j.job_id AS jobId,j.status AS status,COUNT(i.item_id) AS totalItems,COUNT(i.item_id) FILTER(WHERE i.index_status='indexed') AS indexedItems,COUNT(i.item_id) FILTER(WHERE i.index_status='index_failed') AS failedItems FROM knowledge_import_jobs j LEFT JOIN knowledge_import_items i ON i.job_id=j.job_id WHERE j.job_id=#{jobId} GROUP BY j.job_id")
    KnowledgeRepository.JobView job(long jobId);

    @Select(
            "SELECT item_id AS itemId,document_id AS documentId,filename,upload_status AS uploadStatus,index_status AS indexStatus,attempt_count AS attempts,error_code AS errorCode FROM knowledge_import_items WHERE job_id=#{jobId} ORDER BY created_at")
    List<KnowledgeRepository.ItemView> jobItems(long jobId);

    @Select(
            "SELECT event_id AS eventId,event_type AS eventType,payload_json::text AS payload FROM knowledge_import_sse_outbox WHERE job_id=#{jobId} AND event_id>#{afterEventId} ORDER BY event_id")
    List<KnowledgeRepository.JobEvent> jobEvents(
            @Param("jobId") long jobId, @Param("afterEventId") long afterEventId);

    @Update(
            "UPDATE knowledge_import_items SET index_status='pending',attempt_count=0,error_code=NULL,error_summary=NULL,updated_at=CURRENT_TIMESTAMP WHERE item_id=#{itemId} AND index_status='index_failed'")
    int resetItem(long itemId);

    @org.apache.ibatis.annotations.Delete(
            "DELETE FROM knowledge_index_result_inbox WHERE item_id=#{itemId}")
    void deleteResultInbox(long itemId);
}
