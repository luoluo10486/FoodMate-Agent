package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foodmate.application.runtime.port.out.CancellationRepository;
import com.foodmate.application.runtime.port.out.InboxRepository;
import com.foodmate.application.runtime.port.out.RuntimeClientPort;
import com.foodmate.application.runtime.port.out.ToolSkipRepository;
import com.foodmate.application.runtime.service.ToolRegistryService;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import com.foodmate.application.runtime.service.impl.ToolSkipServiceImpl;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1SkipCommand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ToolSkipServiceImplTest {
    private final ToolSkipRepository skips = mock(ToolSkipRepository.class);
    private final InboxRepository inbox = mock(InboxRepository.class);
    private final CancellationRepository dispatches = mock(CancellationRepository.class);
    private final ToolRegistryService registry = mock(ToolRegistryService.class);
    private final IdGenerator ids = mock(IdGenerator.class);
    private final V1RuntimeEventService events = mock(V1RuntimeEventService.class);
    private final RuntimeClientPort client = mock(RuntimeClientPort.class);

    @Test
    void nonOwnerCannotRequestSkip() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(events).requireRunOwner("42", 7L);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service().request(7L, "42", "proposal-1", "工具超时"));

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode());
        verifyNoInteractions(inbox, dispatches, registry, skips);
    }

    @Test
    void missingProposalIsRejected() {
        when(inbox.find("proposal-1")).thenReturn(null);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service().request(7L, "42", "proposal-1", "工具超时"));

        assertEquals(ErrorCode.TOOL_STEP_NOT_FOUND, exception.errorCode());
    }

    @Test
    void proposalFromAnotherRunIsRejected() {
        when(inbox.find("proposal-1")).thenReturn(proposal("claimed", "99", "dispatch-1", 2));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service().request(7L, "42", "proposal-1", "工具超时"));

        assertEquals(ErrorCode.TOOL_STEP_NOT_FOUND, exception.errorCode());
    }

    @Test
    void staleDispatchOrAttemptIsRejected() {
        when(inbox.find("proposal-1")).thenReturn(proposal("claimed", "42", "old-dispatch", 1));
        when(dispatches.findActiveDispatch(42L))
                .thenReturn(
                        new CancellationRepository.ActiveDispatch("dispatch-1", 2, "executing"));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service().request(7L, "42", "proposal-1", "工具超时"));

        assertEquals(ErrorCode.TOOL_STEP_STALE, exception.errorCode());
    }

    @Test
    void nonSkippableToolIsRejected() {
        givenClaimedProposal();
        when(registry.resolve("calculator", null)).thenReturn(tool("calculator", false));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service().request(7L, "42", "proposal-1", "工具超时"));

        assertEquals(ErrorCode.TOOL_SKIP_NOT_ALLOWED, exception.errorCode());
        verify(inbox, never()).requestSkip(any());
    }

    @Test
    void executingAndResolvedStepsAreRejected() {
        givenClaimedProposal("executing");
        when(registry.resolve("calculator", null)).thenReturn(tool("calculator", true));
        BusinessException executing =
                assertThrows(
                        BusinessException.class,
                        () -> service().request(7L, "42", "proposal-1", "工具超时"));
        assertEquals(ErrorCode.TOOL_STEP_ALREADY_RUNNING, executing.errorCode());

        when(inbox.find("proposal-1")).thenReturn(proposal("completed", "42", "dispatch-1", 2));
        BusinessException completed =
                assertThrows(
                        BusinessException.class,
                        () -> service().request(7L, "42", "proposal-1", "工具超时"));
        assertEquals(ErrorCode.TOOL_STEP_ALREADY_RESOLVED, completed.errorCode());
    }

    @Test
    void requestIsIdempotentAndReasonConflictIsRejected() {
        givenClaimedProposal();
        when(registry.resolve("calculator", null)).thenReturn(tool("calculator", true));
        ToolSkipRepository.SkipRecord existing =
                new ToolSkipRepository.SkipRecord(
                        11L,
                        "skip-1",
                        "42",
                        "proposal-1",
                        "inv-1",
                        "calculator",
                        "dispatch-1",
                        2,
                        "sha256:skip",
                        "sha256:proposal",
                        "工具超时",
                        "requested",
                        null,
                        null);
        when(skips.findByProposalId("proposal-1")).thenReturn(existing);

        ToolSkipServiceImpl.SkipResult result = service().request(7L, "42", "proposal-1", "工具超时");
        assertEquals("skip-1", result.skipId());
        verify(inbox, never()).requestSkip(any());

        BusinessException conflict =
                assertThrows(
                        BusinessException.class,
                        () -> service().request(7L, "42", "proposal-1", "用户取消"));
        assertEquals(ErrorCode.CONFLICT, conflict.errorCode());
    }

    @Test
    void requestedSkipPersistsAndPublisherBuildsBoundCommand() {
        givenClaimedProposal();
        when(registry.resolve("calculator", null)).thenReturn(tool("calculator", true));
        when(ids.nextId()).thenReturn(101L);
        when(inbox.requestSkip("proposal-1")).thenReturn(1);

        ToolSkipServiceImpl.SkipResult result = service().request(7L, "42", "proposal-1", "工具超时");

        assertEquals("requested", result.status());
        assertEquals("proposal-1", result.proposalId());
        ArgumentCaptor<ToolSkipRepository.NewSkip> saved =
                ArgumentCaptor.forClass(ToolSkipRepository.NewSkip.class);
        verify(skips).insertRequested(saved.capture());
        assertEquals("calculator", saved.getValue().toolName());
        assertEquals("sha256:proposal", saved.getValue().proposalRequestHash());

        when(skips.findRequested(10))
                .thenReturn(
                        List.of(
                                new ToolSkipRepository.PendingSkip(
                                        101L,
                                        result.skipId(),
                                        "42",
                                        "proposal-1",
                                        "inv-1",
                                        "calculator",
                                        "dispatch-1",
                                        2,
                                        saved.getValue().requestHash(),
                                        saved.getValue().proposalRequestHash(),
                                        "工具超时",
                                        Instant.parse("2026-09-16T00:00:00Z"))));
        when(client.skip(any(V1SkipCommand.class)))
                .thenReturn(new RuntimeClientPort.Response(202, "accepted", "message-1"));

        service().publishRequested();

        ArgumentCaptor<V1SkipCommand> command = ArgumentCaptor.forClass(V1SkipCommand.class);
        verify(client).skip(command.capture());
        assertEquals("42", command.getValue().runId());
        assertEquals("dispatch-1", command.getValue().dispatchId());
        assertEquals("calculator", command.getValue().toolName());
        assertEquals("工具超时", command.getValue().reason());
        verify(skips).markDispatched(101L, "rocketmq", "message-1");
    }

    private ToolSkipServiceImpl service() {
        return new ToolSkipServiceImpl(
                skips, inbox, dispatches, registry, ids, provider(client), events, emptyProvider());
    }

    private void givenClaimedProposal() {
        givenClaimedProposal("claimed");
    }

    private void givenClaimedProposal(String status) {
        when(inbox.find("proposal-1")).thenReturn(proposal(status, "42", "dispatch-1", 2));
        when(dispatches.findActiveDispatch(42L))
                .thenReturn(
                        new CancellationRepository.ActiveDispatch("dispatch-1", 2, "executing"));
        when(skips.findByProposalId("proposal-1")).thenReturn(null);
    }

    private static InboxRepository.InboxRecord proposal(
            String status, String runId, String dispatchId, int attempt) {
        return new InboxRepository.InboxRecord(
                "sha256:proposal", "{}", status, runId, dispatchId, attempt, "inv-1", "calculator");
    }

    private static ToolRegistryService.ToolView tool(String name, boolean skippable) {
        return new ToolRegistryService.ToolView(
                1L, name, name, "工具", "utility", "low", "user", "active", "v1", "v1", null, null,
                null, 1000, true, true, null, 1L, skippable);
    }

    private static <T> ObjectProvider<T> provider(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
