package com.foodmate.api.request.account;

import com.foodmate.shared.runtime.enums.ToolStatus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ToolStatusRequest(
        @NotNull ToolStatus status,
        @Min(1) long revision,
        boolean confirmed,
        String confirmationDigest) {}
