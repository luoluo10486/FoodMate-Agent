package com.foodmate.api.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.shared.account.SessionMode;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionRequest(String title, SessionMode mode) {}
