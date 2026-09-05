package com.foodmate.application.runtime.port.out;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 在持久化边界执行已授权的只读工具契约。 */
public interface ToolGatewayPort {
    /** Checks that a Runtime run is still known to the authority store. */
    boolean runExists(long runId);

    /** Loads the user, session and datasource scope frozen for a Run. */
    RunContext runContext(long runId);

    /** Executes a legacy read-only statement after application validation. */
    List<JsonNode> executeRead(String statement);

    /** Executes the AST-guarded statement with only Java-derived parameters. */
    default List<JsonNode> executeRead(String statement, List<Object> parameters, int timeoutMs) {
        return executeRead(statement);
    }

    /** Persists a redacted SQL operation fact. */
    void audit(Audit audit);

    /** Persists the safe execution summary used by the operational tool-call view. */
    default void recordToolCall(ToolCall toolCall) {}

    record Audit(
            long id,
            long runId,
            String statement,
            String status,
            Integer rows,
            String reason,
            long latencyMs,
            String traceId) {}

    /** Tool execution fact; input and output contain summaries rather than business payloads. */
    record ToolCall(
            long id,
            long runId,
            String toolName,
            String toolVersion,
            String inputJson,
            String outputJson,
            String status,
            Integer latencyMs,
            String errorCode,
            String traceId) {}

    /** Non-secret authorization scope used to bind a SQL execution. */
    record RunContext(long userId, long sessionId, long datasourceId) {
        public RunContext(long userId, long sessionId) {
            this(userId, sessionId, 1L);
        }
    }
}
