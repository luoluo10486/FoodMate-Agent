package com.foodmate.application.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.retention.messaging.DataRetentionTaskPublisher;
import com.foodmate.application.retention.port.out.DataRetentionDatabasePurgePort;
import com.foodmate.application.retention.port.out.DataRetentionRepository;
import com.foodmate.application.retention.service.DataRetentionDeliveryService;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class DataRetentionTaskPublisherTest {
    @Test
    void disabledExecutionDoesNotClaimOrTouchTasks() {
        DataRetentionDeliveryService service = Mockito.mock(DataRetentionDeliveryService.class);
        DataRetentionTaskPublisher publisher =
                new DataRetentionTaskPublisher(
                        service,
                        provider(null),
                        provider(null),
                        false,
                        "rocketmq",
                        "foodmate-private");

        publisher.publishPending();

        verify(service, never()).pending(any(Integer.class));
        verify(service, never()).lease(any(Long.class), anyString(), anyString(), any(Long.class));
    }

    @Test
    void objectStorageTaskIsDeletedAndMarkedSucceeded() {
        DataRetentionDeliveryService service = Mockito.mock(DataRetentionDeliveryService.class);
        ObjectStoragePort storage = Mockito.mock(ObjectStoragePort.class);
        DataRetentionRepository.PurgeTaskSnapshot task =
                snapshot(
                        101L,
                        "object_storage",
                        null,
                        "{\"bucket\":\"foodmate-private\",\"key\":\"knowledge/public/42/guide.md\"}");
        when(service.pending(20)).thenReturn(List.of(task));
        when(service.lease(eq(101L), anyString(), eq("knowledge_document"), eq(42L))).thenReturn(1);

        DataRetentionTaskPublisher publisher =
                new DataRetentionTaskPublisher(
                        service,
                        provider(storage),
                        provider(null),
                        true,
                        "rocketmq",
                        "foodmate-private");

        publisher.publishPending();

        verify(storage).delete("foodmate-private", "knowledge/public/42/guide.md");
        verify(service).succeeded(any(DataRetentionDeliveryService.PurgeExecution.class));
    }

    @Test
    void invalidObjectTargetIsRetriedWithStableErrorCode() {
        DataRetentionDeliveryService service = Mockito.mock(DataRetentionDeliveryService.class);
        ObjectStoragePort storage = Mockito.mock(ObjectStoragePort.class);
        DataRetentionRepository.PurgeTaskSnapshot task =
                snapshot(
                        102L,
                        "object_storage",
                        null,
                        "{\"bucket\":\"foodmate-private\",\"key\":\"knowledge/../secret\"}");
        when(service.pending(20)).thenReturn(List.of(task));
        when(service.lease(eq(102L), anyString(), eq("knowledge_document"), eq(42L))).thenReturn(1);

        DataRetentionTaskPublisher publisher =
                new DataRetentionTaskPublisher(
                        service,
                        provider(storage),
                        provider(null),
                        true,
                        "rocketmq",
                        "foodmate-private");

        publisher.publishPending();

        verify(storage, never()).delete(anyString(), anyString());
        verify(service).retry(eq(102L), anyString(), eq("RETENTION_TASK_FAILED"), anyString());
    }

    @Test
    void vectorTaskPublishesOnlySafeRetentionPayloadAndMarksPublished() {
        DataRetentionDeliveryService service = Mockito.mock(DataRetentionDeliveryService.class);
        MessagePublisherPort broker = Mockito.mock(MessagePublisherPort.class);
        DataRetentionRepository.PurgeTaskSnapshot task =
                snapshot(
                        103L,
                        "vector_index",
                        "foodmate-knowledge-purge-v1",
                        "{\"task_id\":103,\"request_id\":9,\"document_id\":42,\"version\":\"v2\"}");
        when(service.pending(20)).thenReturn(List.of(task));
        when(service.lease(eq(103L), anyString(), eq("knowledge_document"), eq(42L))).thenReturn(1);
        when(broker.publish(any(MessagePublisherPort.PublishRequest.class)))
                .thenReturn(new MessagePublisherPort.PublishResult("broker-103"));

        DataRetentionTaskPublisher publisher =
                new DataRetentionTaskPublisher(
                        service,
                        provider(null),
                        provider(broker),
                        true,
                        "rocketmq",
                        "foodmate-private");

        publisher.publishPending();

        ArgumentCaptor<MessagePublisherPort.PublishRequest> request =
                ArgumentCaptor.forClass(MessagePublisherPort.PublishRequest.class);
        verify(broker).publish(request.capture());
        assertEquals("foodmate-knowledge-purge-v1", request.getValue().topic());
        assertEquals("9", request.getValue().key());
        assertEquals(task.targetRef(), request.getValue().body());
        assertEquals(
                "KnowledgePurge", request.getValue().properties().get("foodmate_message_type"));
        verify(service).published(eq(103L), anyString(), eq("broker-103"));
    }

    @Test
    void hardDeleteDisabledDoesNotClaimEvenWhenRelayIsEnabled() {
        DataRetentionDeliveryService service = Mockito.mock(DataRetentionDeliveryService.class);
        DataRetentionRepository.PurgeTaskSnapshot task =
                snapshot(
                        104L,
                        "vector_index",
                        "foodmate-knowledge-purge-v1",
                        "{\"task_id\":104,\"document_id\":42,\"version\":\"v1\"}",
                        false);
        when(service.pending(20)).thenReturn(List.of(task));

        DataRetentionTaskPublisher publisher =
                new DataRetentionTaskPublisher(
                        service,
                        provider(null),
                        provider(Mockito.mock(MessagePublisherPort.class)),
                        true,
                        "rocketmq",
                        "foodmate-private");

        publisher.publishPending();

        verify(service, never()).lease(any(Long.class), anyString(), anyString(), any(Long.class));
    }

    @Test
    void databaseTaskIsPurgedWhenHardDeleteIsEnabled() {
        DataRetentionDeliveryService service = Mockito.mock(DataRetentionDeliveryService.class);
        DataRetentionDatabasePurgePort database =
                Mockito.mock(DataRetentionDatabasePurgePort.class);
        DataRetentionRepository.PurgeTaskSnapshot task =
                snapshot(
                        105L,
                        "database",
                        null,
                        "{\"resource_type\":\"knowledge_document\",\"resource_id\":42}",
                        true);
        when(service.pending(20)).thenReturn(List.of(task));
        when(service.lease(eq(105L), anyString(), eq("knowledge_document"), eq(42L))).thenReturn(1);
        when(database.purgeWithResult("knowledge_document", 42L))
                .thenReturn(new DataRetentionDatabasePurgePort.PurgeResult("postgresql", 7, true));

        DataRetentionTaskPublisher publisher =
                new DataRetentionTaskPublisher(
                        service,
                        provider(null),
                        provider(null),
                        database,
                        true,
                        "rocketmq",
                        "foodmate-private");

        publisher.publishPending();

        verify(database).purgeWithResult("knowledge_document", 42L);
        verify(service).succeeded(any(DataRetentionDeliveryService.PurgeExecution.class));
    }

    @Test
    void unknownTaskTypeIsRetriedAndNeverSilentlyLeased() {
        DataRetentionDeliveryService service = Mockito.mock(DataRetentionDeliveryService.class);
        DataRetentionRepository.PurgeTaskSnapshot task = snapshot(106L, "unexpected", null, "{}");
        when(service.pending(20)).thenReturn(List.of(task));
        when(service.lease(eq(106L), anyString(), eq("knowledge_document"), eq(42L))).thenReturn(1);

        DataRetentionTaskPublisher publisher =
                new DataRetentionTaskPublisher(
                        service,
                        provider(null),
                        provider(null),
                        true,
                        "rocketmq",
                        "foodmate-private");

        publisher.publishPending();

        verify(service).retry(eq(106L), anyString(), eq("RETENTION_TASK_FAILED"), anyString());
    }

    private static DataRetentionRepository.PurgeTaskSnapshot snapshot(
            long taskId, String taskType, String topic, String targetRef) {
        return snapshot(taskId, taskType, topic, targetRef, true);
    }

    private static DataRetentionRepository.PurgeTaskSnapshot snapshot(
            long taskId,
            String taskType,
            String topic,
            String targetRef,
            boolean hardDeleteEnabled) {
        return new DataRetentionRepository.PurgeTaskSnapshot(
                taskId,
                9L,
                "knowledge_document",
                42L,
                taskType,
                topic,
                targetRef,
                "pending",
                hardDeleteEnabled);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
