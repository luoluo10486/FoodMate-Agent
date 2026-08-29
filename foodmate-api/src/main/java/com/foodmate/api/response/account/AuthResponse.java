package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

/** 登录或注册成功后的认证响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuthResponse(long userId, String username, String role, Instant sessionExpiresAt) {}
