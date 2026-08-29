package com.foodmate.api.request.account;

import jakarta.validation.constraints.NotBlank;

/** 当前用户注销请求参数。 */
public record DeletionRequest(@NotBlank String confirmation, @NotBlank String currentPassword) {}
