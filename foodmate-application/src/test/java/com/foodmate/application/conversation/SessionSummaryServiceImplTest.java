package com.foodmate.application.conversation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.conversation.port.out.ConversationSummaryRepository;
import com.foodmate.application.conversation.service.impl.SessionSummaryServiceImpl;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionSummaryServiceImplTest {
    @Test
    void invalidatedSummaryIsRebuiltEvenWhenMessageCountDidNotChange() {
        ConversationSummaryRepository repository = mock(ConversationSummaryRepository.class);
        when(repository.ownsSession(7L, 9L)).thenReturn(true);
        when(repository.findEffectiveMessages(9L)).thenReturn(messages(9));
        when(repository.lockSummary(9L))
                .thenReturn(new ConversationSummaryRepository.SummarySnapshot(99L, 2, 1, true));
        when(repository.updateSummary(any(ConversationSummaryRepository.UpdatedSummary.class)))
                .thenReturn(1);
        SessionSummaryServiceImpl service = new SessionSummaryServiceImpl(repository, () -> 100L);

        service.maybeRefresh(7L, 9L);

        verify(repository).updateSummary(any(ConversationSummaryRepository.UpdatedSummary.class));
    }

    private static List<ConversationSummaryRepository.MessageSnapshot> messages(int count) {
        List<ConversationSummaryRepository.MessageSnapshot> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            result.add(
                    new ConversationSummaryRepository.MessageSnapshot(
                            i, i, i % 2 == 0 ? "assistant" : "user", "message-" + i));
        }
        return result;
    }
}
