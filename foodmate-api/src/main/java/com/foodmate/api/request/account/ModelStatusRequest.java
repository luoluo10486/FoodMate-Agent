package com.foodmate.api.request.account;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** 模型状态变更请求参数。 */
public record ModelStatusRequest(
        @NotBlank String status,
        @Min(1) long revision,
        boolean confirmed,
        String confirmationDigest) {}
