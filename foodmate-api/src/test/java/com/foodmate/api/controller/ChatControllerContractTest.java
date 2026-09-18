package com.foodmate.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.foodmate.api.controller.runtime.ChatController;
import com.foodmate.api.response.runtime.ChatRunEvent;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.AgentRunCommandService;
import com.foodmate.application.runtime.service.RuntimeCancellationService;
import com.foodmate.application.runtime.service.RuntimeGatewayService;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.id.IdGenerator;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

class ChatControllerContractTest {
    @Test
    void exposesDurableSseCursorInV1ChatHistory() {
        UserAccountService accounts = mock(UserAccountService.class);
        V1RuntimeEventService events = mock(V1RuntimeEventService.class);
        when(accounts.requireSessionUser("session-token"))
                .thenReturn(
                        new UserAccountService.UserRecord(
                                7L,
                                "foodmate-user",
                                "user@example.com",
                                "hash",
                                "FoodMate User",
                                "user",
                                "active"));
        when(events.exists("42")).thenReturn(true);
        when(events.chatEvents("42"))
                .thenReturn(
                        List.of(
                                new V1RuntimeEventService.ChatEvent(
                                        "runtime-event-7",
                                        "sse-19",
                                        "42",
                                        "dispatch-1",
                                        1,
                                        7,
                                        "run.planned",
                                        JsonNodeFactory.instance.objectNode(),
                                        Instant.parse("2026-09-16T00:00:00Z"))));

        ChatController controller =
                new ChatController(
                        mock(RuntimeGatewayService.class),
                        provider(accounts),
                        provider(mock(IdGenerator.class)),
                        provider(mock(AgentRunCommandService.class)),
                        provider(events),
                        provider(mock(RuntimeCancellationService.class)));

        ApiResponse<?> response = controller.events("42", authenticatedRequest());

        @SuppressWarnings("unchecked")
        List<ChatRunEvent> history = (List<ChatRunEvent>) response.data();
        assertEquals("runtime-event-7", history.getFirst().eventId());
        assertEquals("sse-19", history.getFirst().sseEventId());
    }

    private static HttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("foodmate_session", "session-token"));
        return request;
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }
}
