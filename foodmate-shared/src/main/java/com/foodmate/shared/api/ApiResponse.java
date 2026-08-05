package com.foodmate.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.trace.TraceContext;

/** 统一 API 响应包装。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error, ResponseMeta meta) {
    public static <T> ApiResponse<T> success(T data, TraceContext traceContext) {
        return new ApiResponse<>(true, data, null, ResponseMeta.from(traceContext));
    }

    public static ApiResponse<Void> failure(
            ErrorCode code, String message, JsonNode details, TraceContext traceContext) {
        return new ApiResponse<>(
                false,
                null,
                new ErrorBody(
                        code.code(), message == null ? code.defaultMessage() : message, details),
                ResponseMeta.from(traceContext));
    }

    public static ApiResponse<Void> failure(
            ErrorCode code, String message, TraceContext traceContext) {
        return failure(code, message, JsonNodeFactory.instance.objectNode(), traceContext);
    }
}
