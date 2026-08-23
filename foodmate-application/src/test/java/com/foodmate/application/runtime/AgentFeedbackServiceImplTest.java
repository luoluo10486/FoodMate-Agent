package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.runtime.port.out.AgentFeedbackRepository;
import com.foodmate.application.runtime.service.AgentFeedbackService;
import com.foodmate.application.runtime.service.impl.AgentFeedbackServiceImpl;
import com.foodmate.shared.error.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class AgentFeedbackServiceImplTest {
    @Test
    void submitsStructuredFeedbackAndAuditsOnlySafeMetadata() {
        AgentFeedbackRepository store = mock(AgentFeedbackRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(store.target(7L, 42L, 99L))
                .thenReturn(
                        new AgentFeedbackRepository.FeedbackTarget(
                                42L, 99L, "trace-1", "eval-1", "route-2", "prompt-3", "rubric-4"));
        when(store.insert(any())).thenReturn(1);
        AgentFeedbackService service = service(store, audit);

        var result =
                service.submit(
                        7L,
                        42L,
                        99L,
                        new AgentFeedbackService.SubmitCommand(
                                false, List.of("unsafe_or_privacy"), "回答暴露了不应显示的信息", "feedback-1"));

        assertEquals(100L, result.feedbackId());
        assertEquals(List.of("unsafe_or_privacy"), result.reasonCodes());
        assertEquals("sha256:", result.parametersDigest().substring(0, 7));
        ArgumentCaptor<Map<String, ?>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(audit)
                .record(
                        eq(7L),
                        eq("agent_feedback"),
                        eq("100"),
                        eq("agent.feedback.submit"),
                        eq("success"),
                        eq(null),
                        any(),
                        eq("feedback-1"),
                        metadata.capture());
        assertEquals("high", metadata.getValue().get("audit_priority"));
        assertEquals("1", metadata.getValue().get("reason_count"));
    }

    @Test
    void rejectsNegativeFeedbackWithoutReason() {
        AgentFeedbackRepository store = mock(AgentFeedbackRepository.class);
        AgentFeedbackService service = service(store, mock(OperationAuditService.class));

        assertThrows(
                BusinessException.class,
                () ->
                        service.submit(
                                7L,
                                42L,
                                99L,
                                new AgentFeedbackService.SubmitCommand(
                                        false, List.of(), null, "feedback-1")));
    }

    @Test
    void replaysSameIdempotencyKeyWithoutWritingAgain() {
        AgentFeedbackRepository store = mock(AgentFeedbackRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        when(store.target(7L, 42L, 99L))
                .thenReturn(
                        new AgentFeedbackRepository.FeedbackTarget(
                                42L, 99L, "trace-1", null, null, null, null));
        when(store.insert(any())).thenReturn(1);
        AgentFeedbackService service = service(store, audit);

        var created =
                service.submit(
                        7L,
                        42L,
                        99L,
                        new AgentFeedbackService.SubmitCommand(
                                true, List.of(), null, "feedback-1"));
        var previous =
                new AgentFeedbackRepository.FeedbackView(
                        100L,
                        7L,
                        42L,
                        99L,
                        true,
                        List.of(),
                        false,
                        "feedback-1",
                        created.parametersDigest());
        when(store.findByIdempotency(7L, "feedback-1")).thenReturn(previous);

        var result =
                service.submit(
                        7L,
                        42L,
                        99L,
                        new AgentFeedbackService.SubmitCommand(
                                true, List.of(), null, "feedback-1"));

        assertEquals(previous, result);
    }

    private static AgentFeedbackService service(
            AgentFeedbackRepository store, OperationAuditService audit) {
        return new AgentFeedbackServiceImpl(
                provider(store), () -> 100L, provider(audit), true, 1000, true);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
