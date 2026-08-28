package com.foodmate.application.common.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Low-cardinality Agent operation metrics; identifiers are deliberately never metric tags. */
@Service
public class AgentOperationMetrics {
    private static final Set<String> TRANSPORTS = Set.of("http", "rocketmq", "local");
    private static final Set<String> OPERATIONS =
            Set.of("dispatch", "proposal", "result", "event", "sse_replay");
    private static final Set<String> RESULTS =
            Set.of("success", "failed", "duplicate", "rejected", "accepted", "retry", "terminal");
    private static final Set<String> REASONS =
            Set.of(
                    "published",
                    "delivered",
                    "relay_error",
                    "inbox",
                    "consumer_error",
                    "last_event_id",
                    "accepted",
                    "routed",
                    "model_usage",
                    "context_assembled",
                    "tool_proposal",
                    "tool_result",
                    "eval_decided",
                    "answer_stream",
                    "completed",
                    "failed",
                    "cancelled",
                    "checkpoint_saved",
                    "received",
                    "http",
                    "rocketmq");
    private final MeterRegistry registry;
    private final Map<String, AtomicLong> queueDepths = new ConcurrentHashMap<>();

    public AgentOperationMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    public void count(String transport, String operation, String result, String reason) {
        if (registry == null) return;
        Counter.builder("foodmate.agent.operations")
                .description("Agent dispatch, proposal, result, event and replay outcomes")
                .tags(
                        "transport", tag(transport, TRANSPORTS),
                        "operation", tag(operation, OPERATIONS),
                        "result", tag(result, RESULTS),
                        "reason", tag(reason, REASONS))
                .register(registry)
                .increment();
    }

    public void latency(
            String transport, String operation, String result, String reason, long millis) {
        if (registry == null) return;
        Timer.builder("foodmate.agent.terminal.latency")
                .description("Request to Agent terminal state latency")
                .tags(
                        "transport", tag(transport, TRANSPORTS),
                        "operation", tag(operation, OPERATIONS),
                        "result", tag(result, RESULTS),
                        "reason", tag(reason, REASONS))
                .publishPercentiles(0.50, 0.95, 0.99)
                .register(registry)
                .record(Math.max(0, millis), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void queueDepth(String transport, String operation, long value) {
        if (registry == null) return;
        String normalizedTransport = tag(transport, TRANSPORTS);
        String normalizedOperation = tag(operation, OPERATIONS);
        String key = normalizedTransport + ":" + normalizedOperation;
        AtomicLong depth = queueDepths.computeIfAbsent(key, ignored -> new AtomicLong());
        depth.set(Math.max(0, value));
        registry.gauge(
                "foodmate.agent.queue.pending",
                java.util.List.of(
                        io.micrometer.core.instrument.Tag.of("transport", normalizedTransport),
                        io.micrometer.core.instrument.Tag.of("operation", normalizedOperation)),
                depth);
    }

    public Timer.Sample start() {
        return registry == null ? null : Timer.start(registry);
    }

    public void stop(
            Timer.Sample sample, String transport, String operation, String result, String reason) {
        if (sample == null || registry == null) return;
        sample.stop(
                Timer.builder("foodmate.agent.request.latency")
                        .tags(
                                "transport", tag(transport, TRANSPORTS),
                                "operation", tag(operation, OPERATIONS),
                                "result", tag(result, RESULTS),
                                "reason", tag(reason, REASONS))
                        .publishPercentiles(0.50, 0.95, 0.99)
                        .register(registry));
    }

    private static String tag(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) return "unknown";
        String normalized = value.trim().toLowerCase();
        return allowed.contains(normalized) ? normalized : "other";
    }
}
