package com.foodmate.api.request.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Python Runtime 提交给 Java 的工具提案请求参数。 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RuntimeProposalRequest(
        String proposalId,
        String runId,
        String proposalType,
        String schemaVersion,
        String toolName,
        String confirmationRef,
        JsonNode input,
        Payload payload) {
    /** 工具提案中的调用载荷。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(String statement, String invocationId, String idempotencyKey) {}
}
