package com.foodmate.infrastructure.persistence.food;

import com.foodmate.application.food.port.out.ApprovalRequestRepository.*;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 写操作确认事实和审计的 MyBatis 映射。 */
@Mapper
public interface ApprovalRequestMapper {
    @Select(
            "SELECT approval_request_id AS approvalRequestId,user_id AS userId,session_id AS sessionId,agent_run_id AS agentRunId,resource_type AS resourceType,resource_id AS resourceId,operation,parameters_digest AS parametersDigest,status,request_id AS requestId,trace_id AS traceId,idempotency_key AS idempotencyKey,expires_at AS expiresAt,confirmed_at AS confirmedAt,executed_at AS executedAt FROM approval_requests WHERE approval_request_id=#{approvalRequestId} AND user_id=#{userId} AND is_deleted=FALSE")
    ApprovalSnapshot findOwned(
            @Param("userId") long userId, @Param("approvalRequestId") long approvalRequestId);

    @Select(
            "SELECT approval_request_id AS approvalRequestId,user_id AS userId,session_id AS sessionId,agent_run_id AS agentRunId,resource_type AS resourceType,resource_id AS resourceId,operation,parameters_digest AS parametersDigest,status,request_id AS requestId,trace_id AS traceId,idempotency_key AS idempotencyKey,expires_at AS expiresAt,confirmed_at AS confirmedAt,executed_at AS executedAt FROM approval_requests WHERE user_id=#{userId} AND idempotency_key=#{idempotencyKey} AND is_deleted=FALSE")
    ApprovalSnapshot findByIdempotency(
            @Param("userId") long userId, @Param("idempotencyKey") String idempotencyKey);

    @Insert(
            "INSERT INTO approval_requests(approval_request_id,user_id,session_id,agent_run_id,resource_type,resource_id,operation,parameters_digest,status,request_id,trace_id,idempotency_key,expires_at,created_by,updated_by) VALUES (#{approvalRequestId},#{userId},#{sessionId},#{agentRunId},#{resourceType},#{resourceId},#{operation},#{parametersDigest},'pending',#{requestId},#{traceId},#{idempotencyKey},#{expiresAt},#{userId},#{userId}) ON CONFLICT (user_id,idempotency_key) WHERE is_deleted=FALSE DO NOTHING")
    int insert(ApprovalWrite approval);

    @Update(
            "UPDATE approval_requests SET status='confirmed',confirmed_at=#{now},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE approval_request_id=#{approvalRequestId} AND user_id=#{userId} AND status='pending' AND expires_at>#{now} AND is_deleted=FALSE")
    int markConfirmed(
            @Param("userId") long userId,
            @Param("approvalRequestId") long approvalRequestId,
            @Param("now") Instant now);

    @Update(
            "UPDATE approval_requests SET status='expired',updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE approval_request_id=#{approvalRequestId} AND user_id=#{userId} AND status='pending' AND is_deleted=FALSE")
    int markExpired(
            @Param("userId") long userId,
            @Param("approvalRequestId") long approvalRequestId,
            @Param("now") Instant now);

    @Update(
            "UPDATE approval_requests SET status='executed',executed_at=#{now},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE approval_request_id=#{approvalRequestId} AND user_id=#{userId} AND status='confirmed' AND is_deleted=FALSE")
    int markExecuted(
            @Param("userId") long userId,
            @Param("approvalRequestId") long approvalRequestId,
            @Param("now") Instant now);

    @Update(
            "UPDATE approval_requests SET resource_id=#{resourceId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE approval_request_id=#{approvalRequestId} AND user_id=#{userId} AND status='executed' AND is_deleted=FALSE")
    int updateExecutedResource(
            @Param("userId") long userId,
            @Param("approvalRequestId") long approvalRequestId,
            @Param("resourceId") long resourceId,
            @Param("now") Instant now);

    @Insert(
            "INSERT INTO operation_audits(operation_audit_id,operator_id,request_id,trace_id,target_type,target_id,action,result,request_json,response_json,parameters_digest,idempotency_key,created_by,updated_by) VALUES (#{operationAuditId},#{userId},#{requestId},#{traceId},#{targetType},#{targetId},#{action},'success','{}'::jsonb,CAST(#{responseJson} AS jsonb),#{parametersDigest},#{idempotencyKey},#{userId},#{userId}) ON CONFLICT (operator_id,idempotency_key) WHERE operator_id IS NOT NULL AND idempotency_key IS NOT NULL AND is_deleted=FALSE DO NOTHING")
    int insertAudit(AuditWrite audit);
}
