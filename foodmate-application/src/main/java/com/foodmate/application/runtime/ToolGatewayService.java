package com.foodmate.application.runtime;

import com.foodmate.application.runtime.persistence.ToolGatewayStore;
import com.foodmate.shared.id.IdGenerator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Java Tool Gateway：Python 只提交 Proposal，Java 负责权限、SQL Guard、执行和审计。 */
@Service
public class ToolGatewayService {
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_SQL_LENGTH = 8_192;
    private static final Pattern READ_ONLY = Pattern.compile("(?is)^\\s*select\\b.*");
    private static final Pattern FORBIDDEN =
            Pattern.compile(
                    "(?is)(;|\\binsert\\b|\\bupdate\\b|\\bdelete\\b|\\bdrop\\b|\\balter\\b|\\btruncate\\b|\\bgrant\\b|\\brevoke\\b)");
    private final ToolGatewayStore store;
    private final IdGenerator ids;

    public ToolGatewayService(ToolGatewayStore store, IdGenerator ids) {
        this.store = store;
        this.ids = ids;
    }

    /** 执行最小 sql_read Proposal；无数据库时明确返回不可用，不回退到进程内伪造数据。 */
    public ProposalResult execute(Map<String, Object> proposal) {
        String proposalId = text(proposal.get("proposal_id"));
        String runId = text(proposal.get("run_id"));
        String type = text(proposal.get("proposal_type"));
        Map<?, ?> payload = proposal.get("payload") instanceof Map<?, ?> value ? value : Map.of();
        String statement = text(payload.get("statement"));
        String invocationId = text(payload.get("invocation_id"));
        if (!"v1".equals(text(proposal.get("schema_version")))
                || proposalId == null
                || runId == null
                || invocationId == null
                || proposalId.length() > MAX_ID_LENGTH
                || runId.length() > MAX_ID_LENGTH
                || invocationId.length() > MAX_ID_LENGTH
                || !"sql_read".equals(type))
            return reject(proposalId, "PROPOSAL_NOT_ALLOWED");
        if (statement == null
                || statement.length() > MAX_SQL_LENGTH
                || !READ_ONLY.matcher(statement).matches()
                || FORBIDDEN.matcher(statement).find())
            return reject(proposalId, "SQL_PROPOSAL_NOT_READ_ONLY");
        long numericRunId;
        try {
            numericRunId = Long.parseLong(runId);
        } catch (NumberFormatException exception) {
            return reject(proposalId, "RUN_ID_INVALID");
        }
        if (!store.runExists(numericRunId)) {
            return reject(proposalId, "RUN_NOT_FOUND");
        }
        long started = System.nanoTime();
        List<Map<String, Object>> rows;
        try {
            rows = store.executeRead(statement);
            if (rows.size() > 500) rows = rows.subList(0, 500);
            store.audit(
                    new ToolGatewayStore.Audit(
                            ids.nextId(),
                            numericRunId,
                            statement,
                            "executed",
                            rows.size(),
                            null,
                            (System.nanoTime() - started) / 1_000_000,
                            "proposal:" + proposalId));
            return new ProposalResult(proposalId, runId, "succeeded", null, rows);
        } catch (RuntimeException error) {
            store.audit(
                    new ToolGatewayStore.Audit(
                            ids.nextId(),
                            numericRunId,
                            statement,
                            "rejected",
                            null,
                            truncateReason(error.getMessage()),
                            (System.nanoTime() - started) / 1_000_000,
                            "proposal:" + proposalId));
            return new ProposalResult(
                    proposalId, runId, "failed", "SQL_EXECUTION_FAILED", List.of());
        }
    }

    private ProposalResult reject(String proposalId, String code) {
        return new ProposalResult(proposalId, null, "rejected", code, List.of());
    }

    /** 审计表的 reject_reason 是 varchar(255)，避免数据库异常文本反过来遮蔽原始失败结果。 */
    private static String truncateReason(String value) {
        if (value == null || value.isBlank()) return "tool execution failed";
        return value.substring(0, Math.min(255, value.length()));
    }

    private static String text(Object value) {
        if (value == null || value.toString().isBlank() || value.toString().length() > 10000)
            return null;
        return value.toString();
    }

    public record ProposalResult(
            String proposalId,
            String runId,
            String status,
            String errorCode,
            List<Map<String, Object>> rows) {}
}
