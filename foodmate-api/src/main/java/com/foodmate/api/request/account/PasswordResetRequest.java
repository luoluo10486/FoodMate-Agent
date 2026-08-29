package com.foodmate.api.request.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 密码重置邮件请求参数。 */
public record PasswordResetRequest(@NotBlank @Email String email) {}
