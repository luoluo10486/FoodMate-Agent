package com.foodmate.api.request.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 密码重置确认请求参数。 */
public record PasswordResetConfirmRequest(
        @NotBlank String token, @NotBlank @Size(min = 8, max = 128) String newPassword) {}
