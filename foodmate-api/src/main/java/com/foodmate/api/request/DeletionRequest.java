package com.foodmate.api.request;

import jakarta.validation.constraints.NotBlank;

public record DeletionRequest(@NotBlank String confirmation, @NotBlank String currentPassword) {}
