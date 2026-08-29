package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 会话撤销操作响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RevokedSessionsResponse(int revoked, long revision) {}
