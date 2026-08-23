package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.runtime.messaging.RuntimeDlqReplayPublisher;
import com.foodmate.application.runtime.port.out.DeadLetterRepository;
import com.foodmate.application.runtime.port.out.DeadLetterRepository.ReplayOutbox;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class RuntimeDlqReplayPublisherTest {
    @Test
    void publishesOriginalPayloadWithNewBrokerFactAndOriginalIdentityProperties() {
        DeadLetterRepository store = Mockito.mock(DeadLetterRepository.class);
        MessagePublisherPort publisher = Mockito.mock(MessagePublisherPort.class);
        when(store.findPendingReplay(10))
                .thenReturn(
                        List.of(
                                new ReplayOutbox(
                                        901L,
                                        11L,
                                        7L,
                                        "foodmate-java-agent-event-v1",
                                        "foodmate-agent-event-v1",
                                        "broker-original-11",
                                        "run-11",
                                        "11",
                                        "dispatch-11",
                                        1,
                                        "event-11",
                                        4L,
                                        "sha256:request-11",
                                        "{\"event_type\":\"run.completed\"}")));
        when(store.leaseReplay(eq(901L), any(String.class))).thenReturn(1);
        when(publisher.publish(any()))
                .thenReturn(new MessagePublisherPort.PublishResult("broker-new-11"));
        when(store.markReplayPublished(eq(901L), any(String.class), eq("broker-new-11")))
                .thenReturn(1);

        new RuntimeDlqReplayPublisher(store, provider(publisher), "rocketmq").publishPending();

        ArgumentCaptor<MessagePublisherPort.PublishRequest> request =
                ArgumentCaptor.forClass(MessagePublisherPort.PublishRequest.class);
        verify(publisher).publish(request.capture());
        assertEquals("foodmate-agent-event-v1", request.getValue().topic());
        assertEquals("{\"event_type\":\"run.completed\"}", request.getValue().body());
        assertEquals(
                "broker-original-11",
                request.getValue().properties().get("foodmate_original_message_id"));
        assertEquals("901", request.getValue().properties().get("foodmate_replay_id"));
        assertEquals(
                "sha256:request-11", request.getValue().properties().get("foodmate_request_hash"));
        verify(store).markReplayPublished(eq(901L), any(String.class), eq("broker-new-11"));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MessagePublisherPort> provider(MessagePublisherPort publisher) {
        ObjectProvider<MessagePublisherPort> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        return provider;
    }
}
