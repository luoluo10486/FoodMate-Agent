package com.foodmate.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.trace.TraceContext;
import java.util.Map;

/**
 * 统一 API 响应包装。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorBody error,
        ResponseMeta meta
) {
    public static <T> ApiResponse<T> success(T data, TraceContext traceContext) {
        return new ApiResponse<>(true, data, null, ResponseMeta.from(traceContext));
    }

    public static ApiResponse<Void> failure(
            ErrorCode code,
            String message,
            Map<String, Object> details,
            TraceContext traceContext
    ) {
        return new ApiResponse<>(
                false,
                null,
                new ErrorBody(code.code(), message == null ? code.defaultMessage() : message, details),
                ResponseMeta.from(traceContext)
        );
    }
}
