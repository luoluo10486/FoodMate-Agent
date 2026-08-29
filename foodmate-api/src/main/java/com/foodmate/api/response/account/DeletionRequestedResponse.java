package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 账号注销任务创建响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeletionRequestedResponse(long deletionJobId) {}
