package com.foodmate.api.request.conversation;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.shared.conversation.enums.SessionMode;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionRequest(String title, SessionMode mode) {}
