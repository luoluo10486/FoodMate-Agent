package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 管理端导出任务创建响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminExportCreatedResponse(long exportJobId) {}
