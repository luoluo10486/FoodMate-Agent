package com.foodmate.infrastructure.persistence.account;

import com.foodmate.application.account.port.out.AdminAuditReportRepository.DlqSummary;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.KnowledgeImportSummary;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.OperationAuditSummary;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.OutboxSummary;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** PostgreSQL 运营聚合查询；不读取审计参数、消息载荷或错误详情。 */
@Mapper
public interface AdminAuditReportMapper {
    @Select(
            "SELECT COUNT(*) FILTER (WHERE result='pending') AS pending_count,"
                    + "COUNT(*) FILTER (WHERE result='failed') AS failed_count,"
                    + "MIN(created_at) FILTER (WHERE result='pending') AS oldest_pending_at "
                    + "FROM operation_audits WHERE is_deleted=FALSE")
    OperationAuditSummary operationAudits();

    @Select(
            "SELECT COUNT(*) FILTER (WHERE (status='pending' AND next_attempt_at<=#{staleBefore})"
                    + " OR (status='leased' AND lease_until<CURRENT_TIMESTAMP)) AS stale_count,"
                    + "COUNT(*) FILTER (WHERE status='failed') AS failed_count,"
                    + "MIN(CASE WHEN (status='pending' AND next_attempt_at<=#{staleBefore})"
                    + " OR (status='leased' AND lease_until<CURRENT_TIMESTAMP)"
                    + " THEN COALESCE(lease_until,next_attempt_at) END) AS oldest_stale_at "
                    + "FROM runtime_dispatch_outbox")
    OutboxSummary runtimeDispatchOutbox(@Param("staleBefore") Instant staleBefore);

    @Select(
            "SELECT COUNT(*) FILTER (WHERE status='pending' AND available_at<=#{staleBefore})"
                    + " AS stale_count,"
                    + "COUNT(*) FILTER (WHERE status='failed') AS failed_count,"
                    + "MIN(CASE WHEN status='pending' AND available_at<=#{staleBefore}"
                    + " THEN available_at END) AS oldest_stale_at "
                    + "FROM knowledge_index_outbox")
    OutboxSummary knowledgeIndexOutbox(@Param("staleBefore") Instant staleBefore);

    @Select(
            "SELECT COUNT(*) FILTER (WHERE status='pending' AND available_at<=#{staleBefore})"
                    + " AS stale_count,"
                    + "COUNT(*) FILTER (WHERE status='failed') AS failed_count,"
                    + "MIN(CASE WHEN status='pending' AND available_at<=#{staleBefore}"
                    + " THEN available_at END) AS oldest_stale_at "
                    + "FROM knowledge_visibility_outbox")
    OutboxSummary knowledgeVisibilityOutbox(@Param("staleBefore") Instant staleBefore);

    @Select(
            "SELECT COUNT(*) FILTER (WHERE (status='pending' AND next_retry_at<=#{staleBefore})"
                    + " OR (status='leased' AND lease_until<CURRENT_TIMESTAMP)) AS stale_count,"
                    + "COUNT(*) FILTER (WHERE status='dead_letter') AS failed_count,"
                    + "MIN(CASE WHEN (status='pending' AND next_retry_at<=#{staleBefore})"
                    + " OR (status='leased' AND lease_until<CURRENT_TIMESTAMP)"
                    + " THEN COALESCE(lease_until,next_retry_at) END) AS oldest_stale_at "
                    + "FROM agent_run_sse_outbox")
    OutboxSummary agentRunSseOutbox(@Param("staleBefore") Instant staleBefore);

    @Select(
            "SELECT COUNT(*) FILTER (WHERE index_status IN ('pending','parsing','parsed','indexing'))"
                    + " AS pending_count,COUNT(*) FILTER (WHERE index_status='index_failed')"
                    + " AS failed_count,MIN(updated_at) FILTER (WHERE index_status='index_failed')"
                    + " AS oldest_failure_at FROM knowledge_import_items")
    KnowledgeImportSummary knowledgeImports();

    @Select(
            "SELECT COUNT(*) FILTER (WHERE reconciliation_state='pending') AS pending_count,"
                    + "COUNT(*) FILTER (WHERE reconciliation_state='needs_attention')"
                    + " AS needs_attention_count,MIN(first_seen_at) FILTER "
                    + "(WHERE reconciliation_state='pending') AS oldest_pending_at "
                    + "FROM runtime_message_dlq")
    DlqSummary dlq();
}
