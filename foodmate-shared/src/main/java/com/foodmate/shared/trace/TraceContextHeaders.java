package com.foodmate.shared.trace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Builds W3C trace headers while keeping FoodMate's public request/trace IDs compatible. */
public final class TraceContextHeaders {
    private TraceContextHeaders() {}

    /** Returns a valid W3C traceparent with the supplied trace as its parent trace ID. */
    public static String traceparent(TraceContext context) {
        return "00-" + traceId(context.traceId()) + "-" + spanId() + "-01";
    }

    private static String traceId(String value) {
        String candidate = value == null ? "" : value;
        if (candidate.startsWith("trace_")) candidate = candidate.substring("trace_".length());
        if (candidate.matches("[0-9a-fA-F]{32}") && !candidate.matches("0{32}"))
            return candidate.toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(candidate.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String spanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
