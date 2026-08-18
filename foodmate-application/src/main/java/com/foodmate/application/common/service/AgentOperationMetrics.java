package com.foodmate.application.common.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Low-cardinality Agent operation metrics; identifiers are deliberately never metric tags. */
@Service
public class AgentOperationMetrics {
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
                        "transport", tag(transport),
                        "operation", tag(operation),
                        "result", tag(result),
                        "reason", tag(reason))
                .register(registry)
                .increment();
    }

    public void latency(
            String transport, String operation, String result, String reason, long millis) {
        if (registry == null) return;
        Timer.builder("foodmate.agent.terminal.latency")
                .description("Request to Agent terminal state latency")
                .tags(
                        "transport", tag(transport),
                        "operation", tag(operation),
                        "result", tag(result),
                        "reason", tag(reason))
                .publishPercentiles(0.50, 0.95, 0.99)
                .register(registry)
                .record(Math.max(0, millis), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void queueDepth(String transport, String operation, long value) {
        if (registry == null) return;
        String key = tag(transport) + ":" + tag(operation);
        AtomicLong depth = queueDepths.computeIfAbsent(key, ignored -> new AtomicLong());
        depth.set(Math.max(0, value));
        registry.gauge(
                "foodmate.agent.queue.pending",
                java.util.List.of(
                        io.micrometer.core.instrument.Tag.of("transport", tag(transport)),
                        io.micrometer.core.instrument.Tag.of("operation", tag(operation))),
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
                                "transport", tag(transport),
                                "operation", tag(operation),
                                "result", tag(result),
                                "reason", tag(reason))
                        .publishPercentiles(0.50, 0.95, 0.99)
                        .register(registry));
    }

    private static String tag(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}
