package com.foodmate.api.request.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RetentionApprovalRequest(boolean confirmed, String confirmationDigest) {}
