package com.foodmate.api.request;

import jakarta.validation.constraints.Size;

public record MemoryUpdateRequest(
        @Size(max = 4000) String memoryValue, @Size(max = 32) String scope) {}
