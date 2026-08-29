package com.foodmate.api.request.account;

import com.foodmate.shared.runtime.enums.ToolStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 管理员工具状态变更请求参数。 */
public record ToolStatusRequest(
        @NotNull ToolStatus status,
        @Min(1) long revision,
        boolean confirmed,
        String confirmationDigest) {}
