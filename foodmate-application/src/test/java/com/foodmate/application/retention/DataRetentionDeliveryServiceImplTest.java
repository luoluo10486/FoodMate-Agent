package com.foodmate.application.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.retention.port.out.DataRetentionRepository;
import com.foodmate.application.retention.port.out.DataRetentionRepository.PurgeTaskContext;
import com.foodmate.application.retention.service.DataRetentionDeliveryService;
import com.foodmate.application.retention.service.impl.DataRetentionDeliveryServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DataRetentionDeliveryServiceImplTest {
    @Test
    void leaseCarriesResourceIdentitySoDatabaseCanApplyHoldGateAtomically() {
        DataRetentionRepository store = Mockito.mock(DataRetentionRepository.class);
        when(store.leaseTask(103L, "owner-1", "knowledge_document", 42L)).thenReturn(1);
        DataRetentionDeliveryServiceImpl service = new DataRetentionDeliveryServiceImpl(store);

        assertEquals(1, service.lease(103L, "owner-1", "knowledge_document", 42L));

        verify(store).leaseTask(103L, "owner-1", "knowledge_document", 42L);
    }

    @Test
    void duplicatePublishedResultDoesNotRefreshRequest() {
        DataRetentionRepository store = Mockito.mock(DataRetentionRepository.class);
        when(store.applyTaskResult(103L, "succeeded", "", "")).thenReturn(0);
        DataRetentionDeliveryServiceImpl service = new DataRetentionDeliveryServiceImpl(store);

        service.acceptResult(103L, "succeeded", "", "");

        verify(store, never()).refreshPurgeRequest(103L);
    }

    @Test
    void retryRefreshesRequestSoTerminalTaskFailureConverges() {
        DataRetentionRepository store = Mockito.mock(DataRetentionRepository.class);
        DataRetentionDeliveryServiceImpl service = new DataRetentionDeliveryServiceImpl(store);

        service.retry(104L, "owner-1", "RETENTION_TASK_FAILED", "storage unavailable");

        verify(store).retryTask(104L, "owner-1", "RETENTION_TASK_FAILED", "storage unavailable");
        verify(store).refreshPurgeRequest(104L);
    }

    @Test
    void externalResultMustMatchTheImmutableTaskContext() {
        DataRetentionRepository store = Mockito.mock(DataRetentionRepository.class);
        when(store.purgeTaskContext(103L))
                .thenReturn(
                        new PurgeTaskContext(
                                103L, 9L, "knowledge_document", 42L, "vector_index", "v1", 1));
        DataRetentionDeliveryServiceImpl service = new DataRetentionDeliveryServiceImpl(store);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.acceptResult(
                                new DataRetentionDeliveryService.ExternalResult(
                                        103L,
                                        99L,
                                        "knowledge_document",
                                        42L,
                                        "vector_index",
                                        "v1",
                                        "succeeded",
                                        "milvus",
                                        1,
                                        true,
                                        "message-103",
                                        "",
                                        "")));

        verify(store, never()).insertPurgeTaskResult(any());
    }

    @Test
    void duplicateExternalResultHasStableDigestAndOnlyFirstStateChangeConverges() {
        DataRetentionRepository store = Mockito.mock(DataRetentionRepository.class);
        when(store.purgeTaskContext(103L))
                .thenReturn(
                        new PurgeTaskContext(
                                103L, 9L, "knowledge_document", 42L, "vector_index", "v1", 1));
        when(store.insertPurgeTaskResult(any())).thenReturn(1, 0);
        when(store.applyTaskResult(103L, "succeeded", "", "")).thenReturn(1, 0);
        DataRetentionDeliveryServiceImpl service =
                new DataRetentionDeliveryServiceImpl(store, () -> 700L);
        DataRetentionDeliveryService.ExternalResult result =
                new DataRetentionDeliveryService.ExternalResult(
                        103L,
                        9L,
                        "knowledge_document",
                        42L,
                        "vector_index",
                        "v1",
                        "succeeded",
                        "milvus",
                        1,
                        true,
                        "message-103",
                        "",
                        "");

        service.acceptResult(result);
        service.acceptResult(result);

        ArgumentCaptor<DataRetentionRepository.PurgeTaskResult> captor =
                ArgumentCaptor.forClass(DataRetentionRepository.PurgeTaskResult.class);
        verify(store, Mockito.times(2)).insertPurgeTaskResult(captor.capture());
        assertEquals(
                captor.getAllValues().get(0).resultDigest(),
                captor.getAllValues().get(1).resultDigest());
        verify(store).refreshPurgeRequest(103L);
    }
}
