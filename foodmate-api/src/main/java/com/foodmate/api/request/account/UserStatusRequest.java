package com.foodmate.api.request.account;

import com.foodmate.shared.account.enums.UserStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 管理员用户状态变更请求参数。 */
public record UserStatusRequest(
        @NotNull UserStatus status,
        @Min(1) long revision,
        boolean confirmed,
        String confirmationDigest) {}
