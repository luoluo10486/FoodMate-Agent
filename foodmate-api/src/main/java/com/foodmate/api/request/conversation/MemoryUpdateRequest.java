package com.foodmate.api.request.conversation;

import jakarta.validation.constraints.Size;

/** 长期记忆修改请求参数。 */
public record MemoryUpdateRequest(
        @Size(max = 4000) String memoryValue, @Size(max = 32) String scope) {}
