package com.foodmate.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.api.controller.runtime.RunStreamController;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.RuntimeGatewayService;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RunStreamControllerTest {
    private final RuntimeGatewayService gateway = mock(RuntimeGatewayService.class);
    private final UserAccountService accounts = mock(UserAccountService.class);
    private final V1RuntimeEventService events = mock(V1RuntimeEventService.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    @Test
    void chatStreamRoutesNumericV1RunToDurableReplayService() {
        String runId = "349649993432305664";
        when(events.exists(runId)).thenReturn(true);
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
        when(events.cursorFor(runId, "sse_5")).thenReturn(5L);
        when(events.sseEvents(runId, 5L)).thenReturn(List.of());
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            invocation.getArgument(0, Runnable.class).run();
                            return scheduled;
                        })
                .when(scheduler)
                .scheduleWithFixedDelay(
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.any(Instant.class),
                        org.mockito.ArgumentMatchers.any(Duration.class));

        RunStreamController controller =
                new RunStreamController(gateway, provider(accounts), provider(events), scheduler);
        HttpServletRequest request = authenticatedRequest();

        SseEmitter emitter = controller.stream(runId, "sse_5", null, request);

        verify(events).requireRunOwner(runId, 7L);
        verify(events).cursorFor(runId, "sse_5");
        verify(events, timeout(1000)).sseEvents(runId, 5L);
        emitter.complete();
    }

    private HttpServletRequest authenticatedRequest() {
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
