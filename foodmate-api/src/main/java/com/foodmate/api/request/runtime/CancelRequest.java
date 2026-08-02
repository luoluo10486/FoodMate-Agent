package com.foodmate.api.request.runtime;

import jakarta.validation.constraints.NotBlank;

public record CancelRequest(@NotBlank String reason) {}
