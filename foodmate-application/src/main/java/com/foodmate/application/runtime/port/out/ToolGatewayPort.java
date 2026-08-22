package com.foodmate.application.runtime.port.out;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface ToolGatewayPort {
    boolean runExists(long runId);

    RunContext runContext(long runId);

    List<JsonNode> executeRead(String statement);

    /** Executes the AST-guarded statement with only Java-derived parameters. */
    default List<JsonNode> executeRead(String statement, List<Object> parameters, int timeoutMs) {
        return executeRead(statement);
    }

    void audit(Audit audit);

    record Audit(
            long id,
            long runId,
            String statement,
            String status,
            Integer rows,
            String reason,
            long latencyMs,
            String traceId) {}

    record RunContext(long userId, long sessionId, long datasourceId) {
        public RunContext(long userId, long sessionId) {
            this(userId, sessionId, 1L);
        }
    }
}
