package com.foodmate.api.request;

import com.foodmate.shared.account.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull UserStatus status) {}
