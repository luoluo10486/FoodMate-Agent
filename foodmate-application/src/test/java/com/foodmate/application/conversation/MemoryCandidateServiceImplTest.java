package com.foodmate.application.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.conversation.port.out.MemoryRepository;
import com.foodmate.application.conversation.service.MemoryCandidateService;
import com.foodmate.application.conversation.service.SessionSummaryService;
import com.foodmate.application.conversation.service.impl.MemoryCandidateServiceImpl;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        verify(summaries).invalidateForUser(7L);
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
}
