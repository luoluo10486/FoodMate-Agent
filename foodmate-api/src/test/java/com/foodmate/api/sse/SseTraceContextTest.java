package com.foodmate.api.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * SseTraceContext 的上下文传播测试。
 */
class SseTraceContextTest {
    @AfterEach
    void tearDown() {
        TraceContextHolder.clear();
    }

    @Test
    void capturesTraceContextForAsyncEventTask() {
        TraceContext traceContext = TraceContext.of("req_sse", "trace_sse");
        TraceContextHolder.set(traceContext);

        AtomicReference<TraceContext> observed = new AtomicReference<>();
        Runnable task = SseTraceContext.capture(() -> observed.set(TraceContextHolder.current()));

        TraceContextHolder.clear();
        task.run();

        assertEquals(traceContext, observed.get());
        assertNull(TraceContextHolder.current());
    }
}
