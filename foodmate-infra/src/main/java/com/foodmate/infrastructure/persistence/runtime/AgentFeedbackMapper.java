package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.AgentFeedbackRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 结构化 Agent 反馈的唯一 PostgreSQL 写入适配器。 */
@Mapper
public interface AgentFeedbackMapper {
    @Select(
            "SELECT r.agent_run_id AS runId,m.message_id AS messageId,r.trace_id AS traceId,COALESCE(r.result_json->>'eval_id',r.result_json->'eval'->>'eval_id') AS evalId,COALESCE(r.result_json->>'route_version',r.result_json->'model'->>'route_version') AS modelRouteVersion,COALESCE(r.result_json->>'prompt_version',r.result_json->'prompt'->>'version') AS promptVersion,COALESCE(r.result_json->>'rubric_version',r.result_json->'eval'->>'rubric_version') AS rubricVersion FROM agent_runs r JOIN messages m ON m.agent_run_id=r.agent_run_id AND m.message_id=#{messageId} AND m.role='assistant' AND m.is_deleted=FALSE WHERE r.agent_run_id=#{runId} AND r.created_by=#{userId} AND r.status='completed' AND r.is_deleted=FALSE")
    AgentFeedbackRepository.FeedbackTarget target(
            @Param("userId") long userId,
            @Param("runId") long runId,
            @Param("messageId") long messageId);

    @Select(
            "SELECT feedback_id AS feedbackId,user_id AS userId,agent_run_id AS runId,message_id AS messageId,helpful,reason_codes::text AS reasonCodes,high_risk AS highRisk,idempotency_key AS idempotencyKey,parameters_digest AS parametersDigest FROM agent_feedback WHERE user_id=#{userId} AND idempotency_key=#{idempotencyKey} AND is_deleted=FALSE")
    RawFeedbackView findByIdempotency(
            @Param("userId") long userId, @Param("idempotencyKey") String idempotencyKey);

    @Select(
            "SELECT feedback_id AS feedbackId,user_id AS userId,agent_run_id AS runId,message_id AS messageId,helpful,reason_codes::text AS reasonCodes,high_risk AS highRisk,idempotency_key AS idempotencyKey,parameters_digest AS parametersDigest FROM agent_feedback WHERE user_id=#{userId} AND agent_run_id=#{runId} AND message_id=#{messageId} AND is_deleted=FALSE")
    RawFeedbackView findByMessage(
            @Param("userId") long userId,
            @Param("runId") long runId,
            @Param("messageId") long messageId);

    @Insert(
            "INSERT INTO agent_feedback(feedback_id,user_id,agent_run_id,message_id,helpful,reason_codes,comment,trace_id,eval_id,model_route_version,prompt_version,rubric_version,high_risk,idempotency_key,parameters_digest,created_by,updated_by) VALUES(#{feedbackId},#{userId},#{runId},#{messageId},#{helpful},CAST(#{reasonCodes} AS jsonb),#{comment},#{traceId},#{evalId},#{modelRouteVersion},#{promptVersion},#{rubricVersion},#{highRisk},#{idempotencyKey},#{parametersDigest},#{userId},#{userId}) ON CONFLICT DO NOTHING")
    int insert(WriteParameters parameters);

    record RawFeedbackView(
            long feedbackId,
            long userId,
            long runId,
            long messageId,
            boolean helpful,
            String reasonCodes,
            boolean highRisk,
            String idempotencyKey,
            String parametersDigest) {}

    record WriteParameters(
            long feedbackId,
            long userId,
            long runId,
            long messageId,
            boolean helpful,
            String reasonCodes,
            String comment,
            String traceId,
            String evalId,
            String modelRouteVersion,
            String promptVersion,
            String rubricVersion,
            boolean highRisk,
            String idempotencyKey,
            String parametersDigest) {}
}
