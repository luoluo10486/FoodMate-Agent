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
        if (proposalId == null || runId == null || !"sql_read".equals(type))
            return reject(proposalId, "PROPOSAL_NOT_ALLOWED");
        if (statement == null
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
                            error.getMessage(),
                            (System.nanoTime() - started) / 1_000_000,
                            "proposal:" + proposalId));
            return new ProposalResult(
                    proposalId, runId, "failed", "SQL_EXECUTION_FAILED", List.of());
        }
    }

    private ProposalResult reject(String proposalId, String code) {
        return new ProposalResult(proposalId, null, "rejected", code, List.of());
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
