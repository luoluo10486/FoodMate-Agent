package com.foodmate.application.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.common.port.out.OperationAuditPort.AuditRecord;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.shared.id.IdGenerator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class OperationAuditServiceTest {
    @Test
    void recordRemovesSensitiveMetadataAndKeepsOnlySafeSummary() {
        OperationAuditPort store = mock(OperationAuditPort.class);
        ObjectProvider<OperationAuditPort> provider = provider(store);
        when(store.insert(any())).thenReturn(1);
        IdGenerator ids = () -> 123L;
        OperationAuditService service = new OperationAuditService(provider, ids);

        service.record(
                7L,
                "user",
                "7",
                "account.profile.update",
                "success",
                null,
                "sha256:parameters",
                "profile-update-1",
                Map.of("field_count", 2, "password", "must-not-be-persisted", "notes", "private"));

        ArgumentCaptor<AuditRecord> captured = ArgumentCaptor.forClass(AuditRecord.class);
        verify(store).insert(captured.capture());
        assertTrue(captured.getValue().requestJson().contains("field_count"));
        assertFalse(captured.getValue().requestJson().contains("password"));
        assertFalse(captured.getValue().requestJson().contains("private"));
        assertTrue(captured.getValue().responseJson().contains("success"));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<OperationAuditPort> provider(OperationAuditPort store) {
        ObjectProvider<OperationAuditPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        return provider;
    }
}
