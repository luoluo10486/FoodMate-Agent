package com.foodmate.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record BudgetExtensionRequest(
        @Min(1) int additionalTokens,
        @DecimalMin("0.0001") BigDecimal additionalCostCny,
        @NotBlank String confirmationDigest) {}
