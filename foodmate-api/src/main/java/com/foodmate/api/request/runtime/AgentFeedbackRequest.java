package com.foodmate.api.request.runtime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;
import java.util.List;

/** AgentRun 反馈请求参数。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentFeedbackRequest(
        Boolean helpful,
        @Size(max = 6) List<String> reasonCodes,
        @Size(max = 1000) String comment) {}
