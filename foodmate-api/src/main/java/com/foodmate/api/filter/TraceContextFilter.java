package com.foodmate.api.filter;

import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在每个请求中初始化并回写链路上下文。
 */
@Component
public class TraceContextFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = headerOrNew(request, REQUEST_ID_HEADER, "req_");
        String traceId = headerOrNew(request, TRACE_ID_HEADER, "trace_");
        TraceContext traceContext = TraceContext.of(requestId, traceId);
        TraceContextHolder.set(traceContext);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContextHolder.clear();
        }
    }

    private String headerOrNew(HttpServletRequest request, String headerName, String prefix) {
        String value = request.getHeader(headerName);
        if (StringUtils.hasText(value)) {
            return value;
        }
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
