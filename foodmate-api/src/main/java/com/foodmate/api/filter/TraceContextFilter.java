package com.foodmate.api.filter;

import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHeaders;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在每个请求中初始化并回写链路上下文。 */
@Component
public class TraceContextFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    private static final Logger log = LoggerFactory.getLogger(TraceContextFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = headerOrNew(request, REQUEST_ID_HEADER, "req_");
        String traceId = traceId(request);
        TraceContext traceContext = TraceContext.of(requestId, traceId);
        TraceContextHolder.set(traceContext);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(TRACEPARENT_HEADER, TraceContextHeaders.traceparent(traceContext));
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.atInfo()
                    .addKeyValue("http.method", request.getMethod())
                    .addKeyValue("http.path", request.getRequestURI())
                    .addKeyValue("http.status", response.getStatus())
                    .addKeyValue("http.duration_ms", (System.nanoTime() - startedAt) / 1_000_000L)
                    .log("http request completed");
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

    private String traceId(HttpServletRequest request) {
        String explicit = request.getHeader(TRACE_ID_HEADER);
        if (StringUtils.hasText(explicit)) return explicit;
        String traceparent = request.getHeader(TRACEPARENT_HEADER);
        if (StringUtils.hasText(traceparent)) {
            String[] parts = traceparent.trim().split("-");
            if (parts.length == 4
                    && parts[0].matches("[0-9a-fA-F]{2}")
                    && parts[1].matches("[0-9a-fA-F]{32}")
                    && !parts[1].matches("0{32}")
                    && parts[2].matches("[0-9a-fA-F]{16}")
                    && !parts[2].matches("0{16}")
                    && parts[3].matches("[0-9a-fA-F]{2}")) {
                return "trace_" + parts[1].toLowerCase(Locale.ROOT);
            }
        }
        return headerOrNew(request, TRACE_ID_HEADER, "trace_");
    }
}
