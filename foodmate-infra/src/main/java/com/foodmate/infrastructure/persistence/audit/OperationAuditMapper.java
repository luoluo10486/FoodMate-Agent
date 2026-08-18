package com.foodmate.infrastructure.persistence.audit;

import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.common.port.out.OperationAuditPort.AuditRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 统一业务审计的唯一 MyBatis 写入入口。 */
@Mapper
public interface OperationAuditMapper {
    @Insert(
            "INSERT INTO operation_audits(operation_audit_id,operator_id,request_id,trace_id,target_type,target_id,action,result,error_code,request_json,response_json,parameters_digest,idempotency_key,created_by,updated_by) VALUES (#{operationAuditId},#{operatorId},#{requestId},#{traceId},#{targetType},#{targetId},#{action},#{result},#{errorCode},CAST(#{requestJson} AS jsonb),CAST(#{responseJson} AS jsonb),#{parametersDigest},#{idempotencyKey},#{operatorId},#{operatorId}) ON CONFLICT (operator_id,idempotency_key) WHERE operator_id IS NOT NULL AND idempotency_key IS NOT NULL AND is_deleted=FALSE DO NOTHING")
    int insert(AuditRecord record);

    @Insert(
            "INSERT INTO operation_audits(operation_audit_id,operator_id,request_id,trace_id,target_type,target_id,action,result,error_code,request_json,response_json,parameters_digest,idempotency_key,created_by,updated_by) VALUES (#{operationAuditId},#{operatorId},#{requestId},#{traceId},#{targetType},#{targetId},#{action},'pending',#{errorCode},CAST(#{requestJson} AS jsonb),CAST(#{responseJson} AS jsonb),#{parametersDigest},#{idempotencyKey},#{operatorId},#{operatorId}) ON CONFLICT (operator_id,idempotency_key) WHERE operator_id IS NOT NULL AND idempotency_key IS NOT NULL AND is_deleted=FALSE DO NOTHING")
    int reserve(AuditRecord record);

    @Update(
            "UPDATE operation_audits SET result='success',error_code=NULL,response_json=CAST(#{responseJson} AS jsonb),updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE operator_id=#{operatorId} AND idempotency_key=#{idempotencyKey} AND is_deleted=FALSE AND result='pending'")
    int complete(
            @Param("operatorId") long operatorId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("responseJson") String responseJson);

    @Update(
            "UPDATE operation_audits SET result=#{result},error_code=#{errorCode},response_json=CAST(#{responseJson} AS jsonb),updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId} WHERE operator_id=#{operatorId} AND idempotency_key=#{idempotencyKey} AND is_deleted=FALSE AND result='pending'")
    int transition(
            @Param("operatorId") long operatorId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("result") String result,
            @Param("errorCode") String errorCode,
            @Param("responseJson") String responseJson);

    @Select(
            "SELECT parameters_digest AS parametersDigest,result,response_json::text AS responseJson FROM operation_audits WHERE operator_id=#{operatorId} AND idempotency_key=#{idempotencyKey} AND is_deleted=FALSE")
    OperationAuditPort.IdempotencyRecord findIdempotency(
            @Param("operatorId") long operatorId, @Param("idempotencyKey") String idempotencyKey);
}
