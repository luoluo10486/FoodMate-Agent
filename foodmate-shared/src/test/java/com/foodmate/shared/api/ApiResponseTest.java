package com.foodmate.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.trace.TraceContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ApiResponse 的单元测试。
 */
class ApiResponseTest {
    @Test
    void createsSuccessResponseWithTraceMeta() {
        ApiResponse<Map<String, String>> response = ApiResponse.success(
                Map.of("status", "ok"),
                TraceContext.of("req_1", "trace_1")
        );

        assertTrue(response.success());
        assertEquals("ok", response.data().get("status"));
        assertEquals("req_1", response.meta().requestId());
        assertEquals("trace_1", response.meta().traceId());
    }

    @Test
    void createsFailureResponseWithStructuredError() {
        ApiResponse<Void> response = ApiResponse.failure(
                ErrorCode.INVALID_ARGUMENT,
                "bad request",
                Map.of("field", "title"),
                TraceContext.of("req_2", "trace_2")
        );

        assertFalse(response.success());
        assertEquals("INVALID_ARGUMENT", response.error().code());
        assertEquals("title", response.error().details().get("field"));
    }
}
