package com.foodmate.application.runtime.service;

import java.util.List;

/** Parses and authorizes a SQL proposal before it can reach a JDBC executor. */
public interface SqlQueryGuard {
    GuardedQuery guard(
            String statement, SqlSchemaCatalogService.CatalogView catalog, long trustedUserId);

    record GuardedQuery(String statement, List<Object> parameters, int maxRows, int timeoutMs) {}
}
