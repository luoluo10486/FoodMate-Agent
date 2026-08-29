package com.foodmate.application.knowledge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.service.AgentOperationMetrics;
import com.foodmate.application.knowledge.messaging.KnowledgeOutboxPublisher;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeDeliveryService;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class KnowledgeOutboxPublisherTest {
    private final KnowledgeDeliveryService service =
            org.mockito.Mockito.mock(KnowledgeDeliveryService.class);
    private final MessagePublisherPort publisher =
            org.mockito.Mockito.mock(MessagePublisherPort.class);
    private final ObjectProvider<MessagePublisherPort> provider =
            org.mockito.Mockito.mock(ObjectProvider.class);

    private final ObjectProvider<AgentOperationMetrics> metricsProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);

    @Test
    void leasedIndexFactIsPublishedAndAcknowledgedByTheSameOwner() {
        when(provider.getIfAvailable()).thenReturn(publisher);
        when(service.pendingIndex(10))
                .thenReturn(List.of(new KnowledgeRepository.OutboxRow(31L, 41L, "index", "{}")));
        when(service.pendingVisibility(10)).thenReturn(List.of());
        when(service.leaseIndex(eq(31L), anyString())).thenReturn(1);

        KnowledgeOutboxPublisher relay =
                new KnowledgeOutboxPublisher(service, provider, "rocketmq");
        relay.publish();

        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(service).leaseIndex(eq(31L), owner.capture());
        verify(publisher).publish(any(MessagePublisherPort.PublishRequest.class));
        verify(service).publishedIndex(31L, owner.getValue());
    }

    @Test
    void publishFailureRequeuesFactWithTheLeaseOwner() {
        when(provider.getIfAvailable()).thenReturn(publisher);
        when(service.pendingIndex(10))
                .thenReturn(List.of(new KnowledgeRepository.OutboxRow(31L, 41L, "index", "{}")));
        when(service.pendingVisibility(10)).thenReturn(List.of());
        when(service.leaseIndex(eq(31L), anyString())).thenReturn(1);
        when(publisher.publish(any(MessagePublisherPort.PublishRequest.class)))
                .thenThrow(new IllegalStateException("broker unavailable"));

        KnowledgeOutboxPublisher relay =
                new KnowledgeOutboxPublisher(service, provider, "rocketmq");
        relay.publish();

        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(service).leaseIndex(eq(31L), owner.capture());
        verify(service).retryIndex(31L, owner.getValue(), "broker unavailable");
    }

    @Test
    void leasedVisibilityFactIsPublishedWithVisibilityMessageType() {
        when(provider.getIfAvailable()).thenReturn(publisher);
        when(service.pendingIndex(10)).thenReturn(List.of());
        when(service.pendingVisibility(10))
                .thenReturn(
                        List.of(
                                new KnowledgeRepository.OutboxRow(
                                        32L, 42L, "visibility", "{\"visibility\":\"published\"}")));
        when(service.leaseVisibility(eq(32L), anyString())).thenReturn(1);

        KnowledgeOutboxPublisher relay =
                new KnowledgeOutboxPublisher(service, provider, "rocketmq");
        relay.publish();

        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(service).leaseVisibility(eq(32L), owner.capture());
        ArgumentCaptor<MessagePublisherPort.PublishRequest> request =
                ArgumentCaptor.forClass(MessagePublisherPort.PublishRequest.class);
        verify(publisher).publish(request.capture());
        org.junit.jupiter.api.Assertions.assertEquals("visibility", request.getValue().topic());
        org.junit.jupiter.api.Assertions.assertEquals(
                "KnowledgeVisibility",
                request.getValue().properties().get("foodmate_message_type"));
        verify(service).publishedVisibility(32L, owner.getValue());
    }

    @Test
    void recordsLowCardinalityMetricsForIndexRelay() {
        when(provider.getIfAvailable()).thenReturn(publisher);
        when(service.pendingIndex(10))
                .thenReturn(List.of(new KnowledgeRepository.OutboxRow(31L, 41L, "index", "{}")));
        when(service.pendingVisibility(10)).thenReturn(List.of());
        when(service.leaseIndex(eq(31L), anyString())).thenReturn(1);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> registryProvider =
                org.mockito.Mockito.mock(ObjectProvider.class);
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        AgentOperationMetrics metrics = new AgentOperationMetrics(registryProvider);
        when(metricsProvider.getIfAvailable()).thenReturn(metrics);

        KnowledgeOutboxPublisher relay =
                new KnowledgeOutboxPublisher(service, provider, metricsProvider, "rocketmq");
        relay.publish();

        org.junit.jupiter.api.Assertions.assertEquals(
                1.0,
                registry.get("foodmate.agent.operations")
                        .tags(
                                "transport",
                                "rocketmq",
                                "operation",
                                "knowledge_index",
                                "result",
                                "success",
                                "reason",
                                "published")
                        .counter()
                        .count());
    }
}
