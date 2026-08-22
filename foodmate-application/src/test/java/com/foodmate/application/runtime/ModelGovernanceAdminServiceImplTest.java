package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.service.AdminManagementService.AdminWriteCommand;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository;
import com.foodmate.application.runtime.port.out.ModelSecretStatusPort;
import com.foodmate.application.runtime.service.ModelGovernanceAdminService;
import com.foodmate.application.runtime.service.impl.ModelGovernanceAdminServiceImpl;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class ModelGovernanceAdminServiceImplTest {
    private ModelGovernanceAdminRepository store;
    private OperationAuditService audit;
    private IdGenerator ids;
    private ModelGovernanceAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(ModelGovernanceAdminRepository.class);
        audit = Mockito.mock(OperationAuditService.class);
        ids = Mockito.mock(IdGenerator.class);
        ModelSecretStatusPort secrets =
                providerCode -> new ModelSecretStatusPort.SecretStatus(true, "sha256:fingerprint");
        @SuppressWarnings("unchecked")
        ObjectProvider<ModelSecretStatusPort> secretProvider = Mockito.mock(ObjectProvider.class);
        when(secretProvider.getIfAvailable()).thenReturn(secrets);
        service =
                new ModelGovernanceAdminServiceImpl(
                        store, secretProvider, audit, ids, new ObjectMapper());
        when(audit.reserve(
                        any(Long.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any()))
                .thenReturn(1);
        when(audit.findIdempotency(anyLong(), anyString())).thenReturn(null);
    }

    @Test
    void superadminCanUpdateProviderWithMatchingConfirmationAndAudit() {
        when(store.provider("openai"))
                .thenReturn(
                        new ModelGovernanceAdminRepository.ProviderRow(
                                11L, "openai", "OpenAI", "active", "openai.endpoint", 1L));
        when(store.updateProviderStatus("openai", "disabled", 7L, 1L)).thenReturn(1);

        AdminWriteCommand command =
                command(
                        UserRole.SUPERADMIN,
                        1L,
                        "provider-status-1",
                        "model.provider.status.update",
                        "openai",
                        "disabled");

        ModelGovernanceAdminService.MutationResult result =
                service.updateProviderStatus(" openai ", "disabled", command);

        assertEquals(11L, result.resourceId());
        assertEquals("disabled", result.version());
        assertEquals(2L, result.revision());
        verify(store).updateProviderStatus("openai", "disabled", 7L, 1L);
        verify(audit)
                .complete(
                        eq(7L),
                        eq("provider-status-1"),
                        argThat(resultJsonContaining("resourceId", "11")));
    }

    @Test
    void nonSuperadminIsRejectedBeforeMutation() {
        AdminWriteCommand command =
                command(
                        UserRole.ADMIN,
                        1L,
                        "provider-status-operator",
                        "model.provider.status.update",
                        "openai",
                        "disabled");

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.updateProviderStatus("openai", "disabled", command));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
        verify(store, never()).updateProviderStatus(anyString(), anyString(), anyLong(), anyLong());
        verify(audit)
                .recordFailure(
                        eq(7L),
                        eq("model_provider"),
                        eq("openai"),
                        eq("model.provider.status.update"),
                        eq("failed"),
                        eq(ErrorCode.FORBIDDEN.code()),
                        anyString(),
                        eq("provider-status-operator"),
                        any());
    }

    @Test
    void staleProviderRevisionIsRejectedBeforeReservation() {
        when(store.provider("openai"))
                .thenReturn(
                        new ModelGovernanceAdminRepository.ProviderRow(
                                11L, "openai", "OpenAI", "active", "openai.endpoint", 2L));
        AdminWriteCommand command =
                command(
                        UserRole.SUPERADMIN,
                        1L,
                        "provider-status-stale",
                        "model.provider.status.update",
                        "openai",
                        "disabled");

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.updateProviderStatus("openai", "disabled", command));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(audit, never())
                .reserve(
                        any(Long.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any(String.class),
                        any());
        verify(audit)
                .recordFailure(
                        eq(7L),
                        eq("model_provider"),
                        eq("openai"),
                        eq("model.provider.status.update"),
                        eq("failed"),
                        eq(ErrorCode.CONFLICT.code()),
                        anyString(),
                        eq("provider-status-stale"),
                        any());
    }

    @Test
    void viewExposesSecretStatusButNeverSecretValue() {
        when(store.state(any(ModelGovernanceAdminRepository.UsageQuery.class)))
                .thenReturn(
                        new ModelGovernanceAdminRepository.GovernanceState(
                                List.of(
                                        new ModelGovernanceAdminRepository.ProviderRow(
                                                11L,
                                                "openai",
                                                "OpenAI",
                                                "active",
                                                "openai.endpoint",
                                                1L)),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()));

        ModelGovernanceAdminService.GovernanceView view = service.view(null);

        assertEquals(true, view.providers().getFirst().configured());
        assertEquals("sha256:fingerprint", view.providers().getFirst().fingerprint());
    }

    private static AdminWriteCommand command(
            UserRole role, long revision, String key, String action, String target, String value) {
        return new AdminWriteCommand(
                7L,
                role,
                "trace-model-governance",
                key,
                revision,
                true,
                ModelGovernanceAdminServiceImpl.confirmationDigest(
                        action, target, value, revision));
    }

    private static org.mockito.ArgumentMatcher<String> resultJsonContaining(
            String key, String value) {
        return actual -> actual != null && actual.contains("\"" + key + "\":" + value);
    }
}
