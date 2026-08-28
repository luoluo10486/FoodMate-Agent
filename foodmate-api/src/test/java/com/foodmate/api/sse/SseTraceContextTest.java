package com.foodmate.api.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** SseTraceContext 的上下文传播测试。 */
class SseTraceContextTest {
    @AfterEach
    void tearDown() {
        TraceContextHolder.clear();
    }

    @Test
    void capturesTraceContextForAsyncEventTask() {
        TraceContext traceContext = TraceContext.of("req_sse", "trace_sse");
        TraceContextHolder.set(traceContext);
        assertEquals("req_sse", MDC.get(TraceContextHolder.REQUEST_ID_MDC_KEY));

        AtomicReference<TraceContext> observed = new AtomicReference<>();
        AtomicReference<String> observedRequestId = new AtomicReference<>();
        Runnable task =
                SseTraceContext.capture(
                        () -> {
                            observed.set(TraceContextHolder.current());
                            observedRequestId.set(MDC.get(TraceContextHolder.REQUEST_ID_MDC_KEY));
                        });

        TraceContextHolder.clear();
        task.run();

        assertEquals(traceContext, observed.get());
        assertEquals("req_sse", observedRequestId.get());
        assertNull(TraceContextHolder.current());
        assertNull(MDC.get(TraceContextHolder.REQUEST_ID_MDC_KEY));
    }
}
