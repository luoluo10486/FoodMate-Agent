package com.foodmate.application.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.conversation.port.out.MemoryRepository;
import com.foodmate.application.conversation.service.MemoryCandidateService;
import com.foodmate.application.conversation.service.SessionSummaryService;
import com.foodmate.application.conversation.service.impl.MemoryCandidateServiceImpl;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class MemoryCandidateServiceImplTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void persistsTypedMemoryCandidate() throws Exception {
        MemoryRepository repository = mock(MemoryRepository.class);
        IdGenerator ids = () -> 99L;
        SessionSummaryService summaries = mock(SessionSummaryService.class);
        when(repository.findRunOwner(42L)).thenReturn(7L);
        MemoryCandidateServiceImpl service =
                new MemoryCandidateServiceImpl(repository, ids, summaries);

        service.persistFromCompletedRun(
                42L,
                new MemoryCandidateService.CompletedRunPayload(
                        List.of(
                                new MemoryCandidateService.MemoryCandidate(
                                        "preference",
                                        "diet",
                                        mapper.readTree("{\"value\":\"low-salt\"}"),
                                        new BigDecimal("0.90"),
                                        "conversation",
                                        "user",
                                        List.of("message-1")))));

        ArgumentCaptor<MemoryRepository.NewMemory> captor =
                ArgumentCaptor.forClass(MemoryRepository.NewMemory.class);
        verify(repository).insert(captor.capture());
        assertEquals("preference", captor.getValue().type());
        assertEquals("diet", captor.getValue().key());
        assertEquals("{\"value\":\"low-salt\"}", captor.getValue().valueJson());
        assertEquals(new BigDecimal("0.90"), captor.getValue().confidence());
        assertEquals("[\"message-1\"]", captor.getValue().sourceMessageIdsJson());
        verify(summaries).invalidateForUser(7L);
    }

    @Test
    void suppressedSourceCannotCreateMemoryAgain() throws Exception {
        MemoryRepository repository = mock(MemoryRepository.class);
        SessionSummaryService summaries = mock(SessionSummaryService.class);
        when(repository.findRunOwner(42L)).thenReturn(7L);
        when(repository.hasSuppressedSourceMessages(7L, List.of("message-1"))).thenReturn(true);
        MemoryCandidateServiceImpl service =
                new MemoryCandidateServiceImpl(repository, () -> 99L, summaries);

        service.persistFromCompletedRun(
                42L,
                new MemoryCandidateService.CompletedRunPayload(
                        List.of(
                                new MemoryCandidateService.MemoryCandidate(
                                        "preference",
                                        "diet",
                                        mapper.readTree("{\"value\":\"low-salt\"}"),
                                        new BigDecimal("0.90"),
                                        "conversation",
                                        "user",
                                        List.of("message-1")))));

        verify(repository, org.mockito.Mockito.never()).insert(any());
        org.mockito.Mockito.verifyNoInteractions(summaries);
    }

    @Test
    void ignoresCandidateWithoutSourceMessage() throws Exception {
        MemoryRepository repository = mock(MemoryRepository.class);
        SessionSummaryService summaries = mock(SessionSummaryService.class);
        when(repository.findRunOwner(42L)).thenReturn(7L);
        MemoryCandidateServiceImpl service =
                new MemoryCandidateServiceImpl(repository, () -> 99L, summaries);

        service.persistFromCompletedRun(
                42L,
                new MemoryCandidateService.CompletedRunPayload(
                        List.of(
                                new MemoryCandidateService.MemoryCandidate(
                                        "preference",
                                        "diet",
                                        mapper.readTree("{\"value\":\"low-salt\"}"),
                                        new BigDecimal("0.90"),
                                        "conversation",
                                        "user",
                                        List.of()))));

        verify(repository, org.mockito.Mockito.never()).insert(any());
        org.mockito.Mockito.verifyNoInteractions(summaries);
    }

    @Test
    void failedCandidatePersistenceRecordsFailureAudit() throws Exception {
        MemoryRepository repository = mock(MemoryRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        SessionSummaryService summaries = mock(SessionSummaryService.class);
        when(repository.findRunOwner(42L)).thenReturn(7L);
        doThrow(new IllegalStateException("database unavailable")).when(repository).insert(any());
        MemoryCandidateServiceImpl service = service(repository, summaries, audit);

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.persistFromCompletedRun(
                                42L,
                                new MemoryCandidateService.CompletedRunPayload(
                                        List.of(
                                                new MemoryCandidateService.MemoryCandidate(
                                                        "preference",
                                                        "diet",
                                                        mapper.readTree("{\"value\":\"low-salt\"}"),
                                                        new BigDecimal("0.90"),
                                                        "conversation",
                                                        "user",
                                                        List.of("message-1"))))));

        verify(audit)
                .recordFailure(
                        eq(7L),
                        eq("memory"),
                        eq("42"),
                        eq("memory.candidate.persist"),
                        eq("failed"),
                        eq("INTERNAL_ERROR"),
                        isNull(),
                        isNull(),
                        any());
    }

    @Test
    void failedMemoryUpdateRecordsFailureAudit() {
        MemoryRepository repository = mock(MemoryRepository.class);
        OperationAuditService audit = mock(OperationAuditService.class);
        SessionSummaryService summaries = mock(SessionSummaryService.class);
        when(repository.existsOwned(7L, 99L)).thenReturn(true);
        when(repository.updateOwned(7L, 99L, "{}", "user"))
                .thenThrow(new IllegalStateException("database unavailable"));
        MemoryCandidateServiceImpl service = service(repository, summaries, audit);

        assertThrows(IllegalStateException.class, () -> service.update(7L, 99L, null, "user"));

        verify(audit)
                .recordFailure(
                        eq(7L),
                        eq("memory"),
                        eq("99"),
                        eq("memory.update"),
                        eq("failed"),
                        eq("INTERNAL_ERROR"),
                        isNull(),
                        isNull(),
                        any());
    }

    @Test
    void rejectsAuthoritativeMealPlanCandidate() throws Exception {
        MemoryRepository repository = mock(MemoryRepository.class);
        SessionSummaryService summaries = mock(SessionSummaryService.class);
        when(repository.findRunOwner(42L)).thenReturn(7L);
        MemoryCandidateServiceImpl service =
                new MemoryCandidateServiceImpl(repository, () -> 99L, summaries);

        service.persistFromCompletedRun(
                42L,
                new MemoryCandidateService.CompletedRunPayload(
                        List.of(
                                new MemoryCandidateService.MemoryCandidate(
                                        "weekly_recipe",
                                        "week-1",
                                        mapper.readTree("{\"days\":7}"),
                                        new BigDecimal("0.90"),
                                        "conversation",
                                        "user",
                                        List.of("message-1")))));

        verify(repository, org.mockito.Mockito.never()).insert(any());
        org.mockito.Mockito.verifyNoInteractions(summaries);
    }

    @Test
    void rejectsHighImpactHealthFactInCandidateValue() throws Exception {
        MemoryRepository repository = mock(MemoryRepository.class);
        SessionSummaryService summaries = mock(SessionSummaryService.class);
        when(repository.findRunOwner(42L)).thenReturn(7L);
        MemoryCandidateServiceImpl service =
                new MemoryCandidateServiceImpl(repository, () -> 99L, summaries);

        service.persistFromCompletedRun(
                42L,
                new MemoryCandidateService.CompletedRunPayload(
                        List.of(
                                new MemoryCandidateService.MemoryCandidate(
                                        "constraint",
                                        "diet_rule",
                                        mapper.readTree("{\"text\":\"peanut allergy\"}"),
                                        new BigDecimal("0.99"),
                                        "conversation",
                                        "user",
                                        List.of("message-1")))));

        verify(repository, org.mockito.Mockito.never()).insert(any());
        org.mockito.Mockito.verifyNoInteractions(summaries);
    }

    private MemoryCandidateServiceImpl service(
            MemoryRepository repository,
            SessionSummaryService summaries,
            OperationAuditService audit) {
        ObjectProvider<OperationAuditService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(audit);
        return new MemoryCandidateServiceImpl(repository, () -> 99L, summaries, provider);
    }
}
