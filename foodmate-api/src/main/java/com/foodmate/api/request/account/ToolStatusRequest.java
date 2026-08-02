package com.foodmate.api.request.account;

import com.foodmate.shared.runtime.enums.ToolStatus;
import jakarta.validation.constraints.NotNull;

public record ToolStatusRequest(@NotNull ToolStatus status) {}
