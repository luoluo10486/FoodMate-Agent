package com.foodmate.application.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.retention.port.out.DataRetentionRepository;
import com.foodmate.application.retention.service.impl.DataRetentionDeliveryServiceImpl;
import org.junit.jupiter.api.Test;
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
}
