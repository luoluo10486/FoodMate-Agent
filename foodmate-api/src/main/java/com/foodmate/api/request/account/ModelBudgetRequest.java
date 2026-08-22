package com.foodmate.api.request.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ModelBudgetRequest(
        @NotBlank String policyKey,
        @NotBlank String scene,
        @NotBlank String scopeType,
        @Min(1) int maxTotalTokens,
        @NotNull @DecimalMin("0.0") BigDecimal maxCostCny,
        @Min(1) int maxModelCalls,
        @Min(0) int maxStepRetries,
        @NotBlank String windowType,
        @NotBlank String policyVersion,
        @Min(1) long revision,
        boolean confirmed,
        String confirmationDigest) {}
