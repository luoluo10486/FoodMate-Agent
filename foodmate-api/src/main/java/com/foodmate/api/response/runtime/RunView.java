package com.foodmate.api.response.runtime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** AgentRun 概览响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RunView(String runId, String status, int acceptedEventCount) {}
