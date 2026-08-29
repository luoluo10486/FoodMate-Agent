package com.foodmate.application.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** 验证 Agent metrics 不会把动态标识写入标签。 */
class AgentOperationMetricsTest {
    @Test
    void bucketsDynamicTagsIntoOther() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);

        AgentOperationMetrics metrics = new AgentOperationMetrics(provider);
        metrics.count("rocketmq", "dispatch", "success", "run_123_user_456");

        assertEquals(
                1.0,
                registry.get("foodmate.agent.operations")
                        .tags(
                                "transport",
                                "rocketmq",
                                "operation",
                                "dispatch",
                                "result",
                                "success",
                                "reason",
                                "other")
                        .counter()
                        .count());
    }

    @Test
    void exposesPendingAndLeasedQueueDepthWithTheCommonLowCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);

        AgentOperationMetrics metrics = new AgentOperationMetrics(provider);
        metrics.queueDepth("rocketmq", "knowledge_index", "pending", 3);
        metrics.queueDepth("rocketmq", "knowledge_index", "leased", 1);

        assertEquals(
                3.0,
                registry.get("foodmate.agent.queue.depth")
                        .tags(
                                "transport",
                                "rocketmq",
                                "operation",
                                "knowledge_index",
                                "result",
                                "pending",
                                "reason",
                                "queue_depth")
                        .gauge()
                        .value());
        assertEquals(
                1.0,
                registry.get("foodmate.agent.queue.depth")
                        .tags(
                                "transport",
                                "rocketmq",
                                "operation",
                                "knowledge_index",
                                "result",
                                "leased",
                                "reason",
                                "queue_depth")
                        .gauge()
                        .value());
    }

    @Test
    void rejectsDynamicQueueStateAsAnOtherResultWithoutAddingAHighCardinalityTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);

        AgentOperationMetrics metrics = new AgentOperationMetrics(provider);
        metrics.queueDepth("rocketmq", "dispatch", "run-123", 7);

        assertEquals(
                7.0,
                registry.get("foodmate.agent.queue.depth")
                        .tags(
                                "transport",
                                "rocketmq",
                                "operation",
                                "dispatch",
                                "result",
                                "other",
                                "reason",
                                "queue_depth")
                        .gauge()
                        .value());
    }
}
