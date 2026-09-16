package com.foodmate.api.request.runtime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 用户跳过单个工具步骤的请求参数。 */
public record ToolSkipRequest(@NotBlank @Size(max = 256) String reason) {}
