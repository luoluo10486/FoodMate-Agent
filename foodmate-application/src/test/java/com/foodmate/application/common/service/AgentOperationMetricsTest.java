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
}
