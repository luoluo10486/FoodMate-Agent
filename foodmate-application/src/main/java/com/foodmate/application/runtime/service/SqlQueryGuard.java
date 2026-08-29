package com.foodmate.application.runtime.service;

import java.util.List;

/** 在 SQL 提案进入 JDBC 执行器前解析并授权。 */
public interface SqlQueryGuard {
    GuardedQuery guard(
            String statement, SqlSchemaCatalogService.CatalogView catalog, long trustedUserId);

    record GuardedQuery(String statement, List<Object> parameters, int maxRows, int timeoutMs) {}
}
