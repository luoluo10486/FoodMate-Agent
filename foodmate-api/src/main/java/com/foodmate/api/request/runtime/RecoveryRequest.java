package com.foodmate.api.request.runtime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** AgentRun checkpoint 恢复请求参数。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RecoveryRequest(
        @Min(1) int checkpointVersion,
        @NotBlank @Size(max = 71) String checkpointDigest,
        @Size(max = 128) List<@NotBlank @Size(max = 128) String> completedInvocationIds) {}
