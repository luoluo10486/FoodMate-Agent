package com.foodmate.api.request;

import jakarta.validation.constraints.NotBlank;

public record CancelRequest(@NotBlank String reason) {}
