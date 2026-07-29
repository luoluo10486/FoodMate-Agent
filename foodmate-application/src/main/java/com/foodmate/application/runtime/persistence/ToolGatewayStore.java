package com.foodmate.application.runtime.persistence;

import java.util.List;
import java.util.Map;

public interface ToolGatewayStore {
    boolean runExists(long runId);

    List<Map<String, Object>> executeRead(String statement);

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
}
