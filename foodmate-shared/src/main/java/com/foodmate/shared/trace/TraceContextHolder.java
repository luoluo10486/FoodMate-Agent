package com.foodmate.shared.trace;

import java.util.Objects;
import org.slf4j.MDC;

/** 基于 ThreadLocal 保存当前请求的 TraceContext，并把关联标识同步到 SLF4J MDC。 */
public final class TraceContextHolder {
    public static final String REQUEST_ID_MDC_KEY = "request_id";
    public static final String TRACE_ID_MDC_KEY = "trace_id";
    public static final String SESSION_ID_MDC_KEY = "session_id";
    public static final String AGENT_RUN_ID_MDC_KEY = "agent_run_id";

    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    private TraceContextHolder() {}

    public static void set(TraceContext traceContext) {
        Objects.requireNonNull(traceContext, "traceContext must not be null");
        CURRENT.set(traceContext);
        putOrRemove(REQUEST_ID_MDC_KEY, traceContext.requestId());
        putOrRemove(TRACE_ID_MDC_KEY, traceContext.traceId());
        putOrRemove(SESSION_ID_MDC_KEY, traceContext.sessionId());
        putOrRemove(AGENT_RUN_ID_MDC_KEY, traceContext.agentRunId());
    }

    public static TraceContext current() {
        return CURRENT.get();
    }

    public static TraceContext currentOrNew() {
        TraceContext traceContext = CURRENT.get();
        if (traceContext == null) {
            traceContext = TraceContext.newContext();
            set(traceContext);
        }
        return traceContext;
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(REQUEST_ID_MDC_KEY);
        MDC.remove(TRACE_ID_MDC_KEY);
        MDC.remove(SESSION_ID_MDC_KEY);
        MDC.remove(AGENT_RUN_ID_MDC_KEY);
    }

    public static void runWith(TraceContext traceContext, Runnable runnable) {
        Objects.requireNonNull(traceContext, "traceContext must not be null");
        Objects.requireNonNull(runnable, "runnable must not be null");
        TraceContext previous = CURRENT.get();
        set(traceContext);
        try {
            runnable.run();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }

    /** Captures the current context for a task that will execute on another thread. */
    public static Runnable capture(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        TraceContext captured = CURRENT.get();
        return () -> {
            TraceContext previous = CURRENT.get();
            if (captured == null) {
                clear();
            } else {
                set(captured);
            }
            try {
                runnable.run();
            } finally {
                if (previous == null) {
                    clear();
                } else {
                    set(previous);
                }
            }
        };
    }

    private static void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
