package com.foodmate.api.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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

        FilterChain chain = (servletRequest, servletResponse) -> {
            assertEquals("req_header", TraceContextHolder.currentOrNew().requestId());
            assertEquals("trace_header", TraceContextHolder.currentOrNew().traceId());
        };

        filter.doFilter(request, response, chain);

        assertEquals("req_header", response.getHeader(TraceContextFilter.REQUEST_ID_HEADER));
        assertEquals("trace_header", response.getHeader(TraceContextFilter.TRACE_ID_HEADER));
    }
}

