package com.foodmate.infrastructure.persistence.retention;

import com.foodmate.application.retention.port.out.DataRetentionRepository;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** PostgreSQL adapter for retention policy and purge-plan facts. */
@Mapper
public interface DataRetentionMapper {
    @Select(
            "SELECT policy_id AS policyId,resource_type AS resourceType,retention_days AS retentionDays,hard_delete_enabled AS hardDeleteEnabled,policy_version AS policyVersion FROM data_retention_policies WHERE resource_type=#{resourceType} AND status='active'")
    DataRetentionRepository.Policy policy(@Param("resourceType") String resourceType);

    @Select(
            "<script><choose><when test=\"resourceType == 'knowledge_document'\">SELECT #{resourceType} AS resourceType,document_id AS resourceId,is_deleted AS deleted,deleted_at AS deletedAt,revision,storage_key AS storageKey,version FROM knowledge_documents WHERE document_id=#{resourceId} AND is_deleted=TRUE</when><when test=\"resourceType == 'admin_export_job'\">SELECT #{resourceType} AS resourceType,admin_export_job_id AS resourceId,is_deleted AS deleted,deleted_at AS deletedAt,1 AS revision,object_key AS storageKey,NULL AS version FROM admin_export_jobs WHERE admin_export_job_id=#{resourceId} AND is_deleted=TRUE</when></choose></script>")
    DataRetentionRepository.ResourceSnapshot resource(
            @Param("resourceType") String resourceType, @Param("resourceId") long resourceId);

    @Select(
            "SELECT request_id AS requestId,resource_type AS resourceType,resource_id AS resourceId,policy_id AS policyId,requested_by AS requestedBy,status,eligible_at AS eligibleAt,approved_by AS approvedBy,approved_at AS approvedAt,(SELECT COUNT(*) FROM data_purge_tasks t WHERE t.request_id=r.request_id) AS taskCount FROM data_purge_requests r WHERE request_id=#{requestId}")
    DataRetentionRepository.PurgeRequest purgeRequest(@Param("requestId") long requestId);

    @Select(
            "SELECT request_id AS requestId,resource_type AS resourceType,resource_id AS resourceId,policy_id AS policyId,requested_by AS requestedBy,status,eligible_at AS eligibleAt,approved_by AS approvedBy,approved_at AS approvedAt,(SELECT COUNT(*) FROM data_purge_tasks t WHERE t.request_id=r.request_id) AS taskCount FROM data_purge_requests r WHERE requested_by=#{operatorId} AND idempotency_key=#{idempotencyKey}")
    DataRetentionRepository.PurgeRequest purgeRequestByIdempotency(
            @Param("operatorId") long operatorId, @Param("idempotencyKey") String idempotencyKey);

    @Select(
            "SELECT request_id AS requestId,resource_type AS resourceType,resource_id AS resourceId,policy_id AS policyId,requested_by AS requestedBy,status,eligible_at AS eligibleAt,approved_by AS approvedBy,approved_at AS approvedAt,(SELECT COUNT(*) FROM data_purge_tasks t WHERE t.request_id=r.request_id) AS taskCount FROM data_purge_requests r WHERE resource_type=#{resourceType} AND resource_id=#{resourceId} AND status IN ('requested','approved','running') ORDER BY request_id DESC LIMIT 1")
    DataRetentionRepository.PurgeRequest activePurgeRequest(
            @Param("resourceType") String resourceType, @Param("resourceId") long resourceId);

    @Select(
            "SELECT task_type AS taskType,status,attempt_count AS attemptCount,last_error_code AS lastErrorCode FROM data_purge_tasks WHERE request_id=#{requestId} ORDER BY task_type")
    List<DataRetentionRepository.PurgeTaskState> purgeTaskStates(
            @Param("requestId") long requestId);

    @Insert(
            "INSERT INTO data_purge_requests(request_id,resource_type,resource_id,policy_id,requested_by,idempotency_key,deleted_at_snapshot,eligible_at) VALUES(#{request.requestId},#{request.resourceType},#{request.resourceId},#{request.policyId},#{request.requestedBy},#{request.idempotencyKey},#{request.deletedAt},#{request.eligibleAt}) ON CONFLICT DO NOTHING")
    int insertPurgeRequest(@Param("request") DataRetentionRepository.NewPurgeRequest request);

    @Update(
            "UPDATE data_purge_requests SET status='approved',approved_by=#{approverId},approved_at=#{approvedAt},updated_at=CURRENT_TIMESTAMP WHERE request_id=#{requestId} AND status='requested' AND NOT EXISTS (SELECT 1 FROM data_legal_holds h WHERE h.resource_type=data_purge_requests.resource_type AND h.resource_id=data_purge_requests.resource_id AND h.status='active')")
    int approvePurge(
            @Param("requestId") long requestId,
            @Param("approverId") long approverId,
            @Param("approvedAt") Instant approvedAt);

    @Insert(
            "INSERT INTO data_purge_tasks(task_id,request_id,task_type,topic,target_ref) VALUES(#{task.taskId},#{task.requestId},#{task.taskType},#{task.topic},CAST(#{task.targetRef} AS jsonb)) ON CONFLICT (request_id,task_type) DO NOTHING")
    int insertPurgeTask(@Param("task") DataRetentionRepository.PurgeTask task);

    @Select(
            "SELECT t.task_id AS taskId,t.request_id AS requestId,r.resource_type AS resourceType,r.resource_id AS resourceId,t.task_type AS taskType,t.topic,t.target_ref::text AS targetRef,t.status,p.hard_delete_enabled AS hardDeleteEnabled FROM data_purge_tasks t JOIN data_purge_requests r ON r.request_id=t.request_id JOIN data_retention_policies p ON p.policy_id=r.policy_id WHERE t.next_attempt_at<=CURRENT_TIMESTAMP AND ((t.status='pending') OR (t.status='leased' AND t.lease_until<CURRENT_TIMESTAMP)) AND (t.task_type<>'database' OR NOT EXISTS (SELECT 1 FROM data_purge_tasks prerequisite WHERE prerequisite.request_id=t.request_id AND prerequisite.task_type IN ('object_storage','vector_index') AND prerequisite.status<>'succeeded')) ORDER BY t.created_at LIMIT #{limit}")
    List<DataRetentionRepository.PurgeTaskSnapshot> pendingTasks(@Param("limit") int limit);

    @Update(
            "UPDATE data_purge_tasks SET status='leased',owner_token=#{owner},lease_until=CURRENT_TIMESTAMP+INTERVAL '30 seconds',attempt_count=attempt_count+1,updated_at=CURRENT_TIMESTAMP WHERE task_id=#{taskId} AND ((status='pending' AND next_attempt_at<=CURRENT_TIMESTAMP) OR (status='leased' AND lease_until<CURRENT_TIMESTAMP)) AND NOT EXISTS (SELECT 1 FROM data_legal_holds h WHERE h.resource_type=#{resourceType} AND h.resource_id=#{resourceId} AND h.status='active')")
    int leaseTask(
            @Param("taskId") long taskId,
            @Param("owner") String owner,
            @Param("resourceType") String resourceType,
            @Param("resourceId") long resourceId);

    @Update(
            "UPDATE data_purge_tasks SET status='published',owner_token=NULL,lease_until=NULL,published_message_id=#{messageId},updated_at=CURRENT_TIMESTAMP WHERE task_id=#{taskId} AND owner_token=#{owner} AND status='leased'")
    int markTaskPublished(
            @Param("taskId") long taskId,
            @Param("owner") String owner,
            @Param("messageId") String messageId);

    @Update(
            "UPDATE data_purge_tasks SET status='succeeded',owner_token=NULL,lease_until=NULL,last_error_code=#{errorCode},last_error_summary=#{errorSummary},completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE task_id=#{taskId} AND owner_token=#{owner} AND status='leased'")
    int markTaskSucceeded(
            @Param("taskId") long taskId,
            @Param("owner") String owner,
            @Param("errorCode") String errorCode,
            @Param("errorSummary") String errorSummary);

    @Update(
            "UPDATE data_purge_tasks SET status=CASE WHEN attempt_count>=3 THEN 'failed' ELSE 'pending' END,owner_token=NULL,lease_until=NULL,last_error_code=#{errorCode},last_error_summary=#{errorSummary},next_attempt_at=CURRENT_TIMESTAMP+(LEAST(60,POWER(2,GREATEST(attempt_count-1,0)))*INTERVAL '1 second'),updated_at=CURRENT_TIMESTAMP WHERE task_id=#{taskId} AND owner_token=#{owner} AND status='leased'")
    void retryTask(
            @Param("taskId") long taskId,
            @Param("owner") String owner,
            @Param("errorCode") String errorCode,
            @Param("errorSummary") String errorSummary);

    @Update(
            "UPDATE data_purge_tasks SET status=CASE WHEN #{status}='succeeded' THEN 'succeeded' WHEN attempt_count>=3 THEN 'failed' ELSE 'pending' END,owner_token=NULL,lease_until=NULL,last_error_code=#{errorCode},last_error_summary=#{errorSummary},next_attempt_at=CASE WHEN #{status}='succeeded' THEN CURRENT_TIMESTAMP ELSE CURRENT_TIMESTAMP+(LEAST(60,POWER(2,GREATEST(attempt_count-1,0)))*INTERVAL '1 second') END,completed_at=CASE WHEN #{status}='succeeded' THEN CURRENT_TIMESTAMP ELSE NULL END,updated_at=CURRENT_TIMESTAMP WHERE task_id=#{taskId} AND status='published'")
    int applyTaskResult(
            @Param("taskId") long taskId,
            @Param("status") String status,
            @Param("errorCode") String errorCode,
            @Param("errorSummary") String errorSummary);

    @Update(
            "UPDATE data_purge_requests r SET status=CASE WHEN EXISTS(SELECT 1 FROM data_purge_tasks t WHERE t.request_id=r.request_id AND t.status='failed') THEN 'failed' WHEN NOT EXISTS(SELECT 1 FROM data_purge_tasks t WHERE t.request_id=r.request_id AND t.status<>'succeeded') THEN 'completed' WHEN EXISTS(SELECT 1 FROM data_purge_tasks t WHERE t.request_id=r.request_id AND t.status IN ('published','succeeded')) THEN 'running' ELSE 'approved' END,completed_at=CASE WHEN NOT EXISTS(SELECT 1 FROM data_purge_tasks t WHERE t.request_id=r.request_id AND t.status<>'succeeded') THEN CURRENT_TIMESTAMP ELSE r.completed_at END,updated_at=CURRENT_TIMESTAMP WHERE r.request_id=(SELECT request_id FROM data_purge_tasks WHERE task_id=#{taskId}) AND r.status IN ('approved','running')")
    void refreshPurgeRequest(@Param("taskId") long taskId);

    @Insert(
            "INSERT INTO data_legal_holds(hold_id,resource_type,resource_id,reason_code,placed_by) VALUES(#{hold.holdId},#{hold.resourceType},#{hold.resourceId},#{hold.reasonCode},#{hold.placedBy}) ON CONFLICT DO NOTHING")
    int insertHold(@Param("hold") DataRetentionRepository.NewHold hold);

    @Select(
            "SELECT hold_id AS holdId,resource_type AS resourceType,resource_id AS resourceId,reason_code AS reasonCode,placed_by AS placedBy,status,placed_at AS placedAt FROM data_legal_holds WHERE resource_type=#{resourceType} AND resource_id=#{resourceId} AND status='active' ORDER BY hold_id DESC LIMIT 1")
    DataRetentionRepository.Hold activeHold(
            @Param("resourceType") String resourceType, @Param("resourceId") long resourceId);

    @Select(
            "SELECT hold_id AS holdId,resource_type AS resourceType,resource_id AS resourceId,reason_code AS reasonCode,placed_by AS placedBy,status,placed_at AS placedAt FROM data_legal_holds WHERE hold_id=#{holdId}")
    DataRetentionRepository.Hold hold(@Param("holdId") long holdId);

    @Update(
            "UPDATE data_legal_holds SET status='released',released_by=#{operatorId},released_at=#{releasedAt} WHERE hold_id=#{holdId} AND status='active'")
    int releaseHold(
            @Param("holdId") long holdId,
            @Param("operatorId") long operatorId,
            @Param("releasedAt") Instant releasedAt);
}
