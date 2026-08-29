package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 当前用户公开资料响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserResponse(
        long userId,
        String username,
        String email,
        String nickname,
        String role,
        String status,
        String avatarUrl) {}
