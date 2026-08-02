package com.foodmate.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank String token, @NotBlank @Size(min = 8, max = 128) String newPassword) {}
