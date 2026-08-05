package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** V1 Java -> Python tool result message. */
public record V1ToolResult(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("proposal_id") String proposalId,
        @JsonProperty("request_hash") String requestHash,
        @JsonProperty("run_id") String runId,
        @JsonProperty("invocation_id") String invocationId,
        String status,
        @JsonProperty("error_code") String errorCode,
        List<JsonNode> rows) {}
