package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.account.service.AdminOperationalQueryService;

import java.util.List;

/** 统一管理查询响应；items 仅包含对应资源的安全摘要字段。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminOperationalQueryResponse(
        String resource, List<?> items, long total, int page, int size) {
    public static AdminOperationalQueryResponse from(
            String resource, AdminOperationalQueryService.Page<?> value) {
        return new AdminOperationalQueryResponse(
                resource, value.items(), value.total(), value.page(), value.size());
    }
}
