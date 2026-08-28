package com.foodmate.api.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** TraceContextFilter 的单元测试。 */
class TraceContextFilterTest {
    private final TraceContextFilter filter = new TraceContextFilter();

    @AfterEach
    void tearDown() {
        TraceContextHolder.clear();
    }

    @Test
    void propagatesRequestAndTraceHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContextFilter.REQUEST_ID_HEADER, "req_header");
        request.addHeader(TraceContextFilter.TRACE_ID_HEADER, "trace_header");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain =
                (servletRequest, servletResponse) -> {
                    assertEquals("req_header", TraceContextHolder.currentOrNew().requestId());
                    assertEquals("trace_header", TraceContextHolder.currentOrNew().traceId());
                    assertEquals("req_header", MDC.get(TraceContextHolder.REQUEST_ID_MDC_KEY));
                    assertEquals("trace_header", MDC.get(TraceContextHolder.TRACE_ID_MDC_KEY));
                };

        filter.doFilter(request, response, chain);

        assertEquals("req_header", response.getHeader(TraceContextFilter.REQUEST_ID_HEADER));
        assertEquals("trace_header", response.getHeader(TraceContextFilter.TRACE_ID_HEADER));
        assertNotNull(response.getHeader(TraceContextFilter.TRACEPARENT_HEADER));
        assertNull(TraceContextHolder.current());
        assertNull(MDC.get(TraceContextHolder.REQUEST_ID_MDC_KEY));
    }
}
