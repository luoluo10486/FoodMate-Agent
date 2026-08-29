package com.foodmate.api.request.runtime;

import jakarta.validation.constraints.NotBlank;

/** AgentRun 取消请求参数。 */
public record CancelRequest(@NotBlank String reason) {}
