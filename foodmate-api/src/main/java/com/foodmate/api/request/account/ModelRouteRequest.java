package com.foodmate.api.request.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ModelRouteRequest(
        @NotBlank String providerCode,
        @NotBlank String modelName,
        String fallbackProviderCode,
        String fallbackModelName,
        @Min(0) int priority,
        @NotBlank String routeVersion,
        @NotBlank String priceVersion,
        @NotBlank String budgetPolicyVersion,
        @DecimalMin("0.0") java.math.BigDecimal maxCost,
        @Min(1) Integer maxLatencyMs,
        @NotBlank String status,
        @Min(1) long revision,
        boolean confirmed,
        String confirmationDigest) {}
