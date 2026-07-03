package com.foodmate.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.foodmate.shared.trace.TraceContext;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseMeta(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("trace_id") String traceId
) {
    public static ResponseMeta from(TraceContext traceContext) {
        return new ResponseMeta(traceContext.requestId(), traceContext.traceId());
    }
}

