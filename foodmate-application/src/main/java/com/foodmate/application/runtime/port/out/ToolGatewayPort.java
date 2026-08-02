package com.foodmate.application.runtime.port.out;

import java.util.List;
import java.util.Map;

public interface ToolGatewayPort {
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
