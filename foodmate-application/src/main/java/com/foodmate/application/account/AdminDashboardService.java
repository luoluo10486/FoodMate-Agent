package com.foodmate.application.account;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {
    private final AdminDashboardStore store;

    public AdminDashboardService(AdminDashboardStore store) {
        this.store = store;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> row = store.overview();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
                "overview_metrics",
                List.of(
                        Map.of(
                                "label",
                                "AgentRun 今日",
                                "value",
                                String.valueOf(row.get("runs_today")),
                                "hint",
                                "实时",
                                "tone",
                                "green"),
                        Map.of(
                                "label",
                                "失败率",
                                "value",
                                row.get("failure_rate") + "%",
                                "hint",
                                "全部记录",
                                "tone",
                                "danger"),
                        Map.of(
                                "label",
                                "模型调用",
                                "value",
                                String.valueOf(store.modelUsageCount()),
                                "hint",
                                "累计",
                                "tone",
                                "orange"),
                        Map.of(
                                "label",
                                "知识文档",
                                "value",
                                String.valueOf(store.knowledgeCount()),
                                "hint",
                                "实际记录",
                                "tone",
                                "blue")));
        result.put("runs", store.runs());
        result.put("tool_calls", store.toolCalls());
        result.put("sql_audits", store.sqlAudits());
        result.put("tools", store.tools());
        result.put("usage", store.usage());
        result.put("knowledge", store.knowledge());
        result.put("deleted", store.deleted());
        result.put("operation_audits", store.operationAudits());
        return result;
    }
}
