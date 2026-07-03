package com.foodmate.api.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private static final ch.qos.logback.classic.Logger HANDLER_LOGGER =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static ch.qos.logback.classic.Level previousLevel;

    @BeforeAll
    static void muteExpectedUnknownExceptionLog() {
        previousLevel = HANDLER_LOGGER.getLevel();
        HANDLER_LOGGER.setLevel(ch.qos.logback.classic.Level.OFF);
    }

    @AfterAll
    static void restoreExpectedUnknownExceptionLog() {
        HANDLER_LOGGER.setLevel(previousLevel);
    }

    @AfterEach
    void tearDown() {
        TraceContextHolder.clear();
    }

    @Test
    void mapsBusinessExceptionToStructuredError() {
        TraceContextHolder.set(TraceContext.of("req_test", "trace_test"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.CONFLICT, "duplicated", Map.of("field", "username"))
        );

        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("CONFLICT", response.getBody().error().code());
        assertEquals("req_test", response.getBody().meta().requestId());
    }

    @Test
    void mapsUnknownExceptionWithoutLeakingStackTrace() {
        TraceContextHolder.set(TraceContext.of("req_test", "trace_test"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnknownException(new RuntimeException("boom"));

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().error().code());
        assertEquals("系统异常", response.getBody().error().message());
    }
}
