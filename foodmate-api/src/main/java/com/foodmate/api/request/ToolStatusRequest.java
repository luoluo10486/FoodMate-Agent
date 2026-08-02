package com.foodmate.api.request;

import com.foodmate.shared.account.ToolStatus;
import jakarta.validation.constraints.NotNull;

public record ToolStatusRequest(@NotNull ToolStatus status) {}
