package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 管理员凭证重置通知响应；响应不包含重置令牌。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CredentialResetResponse(boolean requested, long revision) {}
