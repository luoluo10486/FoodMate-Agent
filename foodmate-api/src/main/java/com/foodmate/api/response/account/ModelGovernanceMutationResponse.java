package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 模型治理写操作响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ModelGovernanceMutationResponse(
        boolean changed, long resourceId, String version, long revision) {}
