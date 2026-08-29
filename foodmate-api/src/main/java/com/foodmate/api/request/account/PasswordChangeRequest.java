package com.foodmate.api.request.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前用户修改密码请求参数。 */
public record PasswordChangeRequest(
        @NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 128) String newPassword) {}
