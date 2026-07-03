package com.foodmate.shared.trace;

import java.util.Objects;

public final class TraceContextHolder {
    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    private TraceContextHolder() {
    }

    public static void set(TraceContext traceContext) {
        CURRENT.set(traceContext);
    }

    public static TraceContext current() {
        return CURRENT.get();
    }

    public static TraceContext currentOrNew() {
        TraceContext traceContext = CURRENT.get();
        if (traceContext == null) {
            traceContext = TraceContext.newContext();
            CURRENT.set(traceContext);
        }
        return traceContext;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void runWith(TraceContext traceContext, Runnable runnable) {
        Objects.requireNonNull(traceContext, "traceContext must not be null");
        Objects.requireNonNull(runnable, "runnable must not be null");
        TraceContext previous = CURRENT.get();
        CURRENT.set(traceContext);
        try {
            runnable.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
