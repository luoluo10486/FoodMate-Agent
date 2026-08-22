package com.foodmate.application.knowledge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.knowledge.messaging.KnowledgeOutboxPublisher;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeDeliveryService;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
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
}
