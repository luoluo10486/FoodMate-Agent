package com.foodmate.api.request.account;

import jakarta.validation.constraints.Min;

/** 管理端恢复和会话撤销共用的并发与确认参数。 */
public record AdminMutationRequest(
        @Min(1) long revision, boolean confirmed, String confirmationDigest) {}
