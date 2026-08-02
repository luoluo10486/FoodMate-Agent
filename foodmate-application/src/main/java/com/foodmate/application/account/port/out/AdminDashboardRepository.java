package com.foodmate.application.account.port.out;

import java.util.List;
import java.util.Map;

public interface AdminDashboardRepository {
    Map<String, Object> overview();

    long modelUsageCount();

    long knowledgeCount();

    List<Map<String, Object>> runs();

    List<Map<String, Object>> toolCalls();

    List<Map<String, Object>> sqlAudits();

    List<Map<String, Object>> tools();

    List<Map<String, Object>> usage();

    List<Map<String, Object>> knowledge();

    List<Map<String, Object>> deleted();

    List<Map<String, Object>> operationAudits();
}
