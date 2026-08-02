package com.foodmate.api.request.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 128) String newPassword) {}
