package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.conversation.service.SessionSummaryService;
import com.foodmate.application.runtime.admission.AgentAdmissionService;
import com.foodmate.application.runtime.command.AgentRunBudgetDefaults;
import com.foodmate.application.runtime.port.out.AgentRunCommandRepository;
import com.foodmate.application.runtime.service.impl.AgentRunCommandServiceImpl;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.RuntimeException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class AgentRunCommandServiceImplTest {
    @Test
    void creationFailureRecordsSafeAudit() {
        UserAccountService accounts = mock(UserAccountService.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        doThrow(new IllegalStateException("message store unavailable"))
                .when(accounts)
                .addMessage(anyLong(), anyLong(), anyString(), anyString(), any(), any());
        AgentRunCommandServiceImpl service = service(null, accounts, audit);

        assertThrows(
                IllegalStateException.class,
                () -> service.createUserMessageRunDetails(7L, 9L, "private message", "trace-1"));

        ArgumentCaptor<Map<String, ?>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(audit)
                .recordFailure(
                        eq(7L),
                        eq("agent_run"),
                        eq("100"),
                        eq("agent_run.create"),
                        eq("failed"),
                        eq(ErrorCode.INTERNAL_ERROR.code()),
                        isNull(),
                        isNull(),
                        metadata.capture());
        assertFalse(metadata.getValue().containsKey("content"));
    }

    @Test
    void parentSupersedeFailureRecordsSeparateAuditFacts() {
        AgentRunCommandRepository store = mock(AgentRunCommandRepository.class);
        UserAccountService accounts = mock(UserAccountService.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(store.waitingRun(9L)).thenReturn(7L);
        when(accounts.addMessage(anyLong(), anyLong(), anyString(), anyString(), any(), eq(100L)))
                .thenReturn(
                        new UserAccountService.MessageRecord(
                                101L, 9L, 100L, "user", "continuation", "{}", 1, Instant.now()));
        when(store.supersede(7L, 100L)).thenReturn(0);
        AgentRunCommandServiceImpl service = service(store, accounts, audit);

        assertThrows(
                RuntimeException.class,
                () -> service.createUserMessageRunDetails(7L, 9L, "continuation", "trace-1"));

        verify(audit)
                .recordFailure(
                        eq(7L),
                        eq("agent_run"),
                        eq("7"),
                        eq("agent_run.superseded"),
                        eq("failed"),
                        eq("RUNTIME_STATE_CONFLICT"),
                        isNull(),
                        isNull(),
                        any());
    }

    private static AgentRunCommandServiceImpl service(
            AgentRunCommandRepository store,
            UserAccountService accounts,
            OperationAuditService audit) {
        IdGenerator ids = () -> 100L;
        return new AgentRunCommandServiceImpl(
                provider(store),
                ids,
                accounts,
                mock(AgentRunBudgetDefaults.class),
                mock(AgentAdmissionService.class),
                mock(SessionSummaryService.class),
                provider(audit),
                provider(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
