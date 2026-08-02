package com.foodmate.api.request.account;

import jakarta.validation.constraints.NotBlank;

public record DeletionRequest(@NotBlank String confirmation, @NotBlank String currentPassword) {}
