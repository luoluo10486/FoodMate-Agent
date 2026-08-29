package com.foodmate.api.request.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/** 模型供应商价格写入请求参数。 */
public record ModelPriceRequest(
        @NotBlank String providerCode,
        @NotBlank String modelName,
        @NotBlank String priceVersion,
        @NotNull @DecimalMin("0.0") BigDecimal inputPricePerMillion,
        @NotNull @DecimalMin("0.0") BigDecimal outputPricePerMillion,
        @NotBlank String currency,
        @NotNull Instant effectiveAt,
        @Min(1) long revision,
        boolean confirmed,
        String confirmationDigest) {}
