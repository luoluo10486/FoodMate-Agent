package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 资源恢复操作响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RestoreResponse(boolean restored, long revision) {}
