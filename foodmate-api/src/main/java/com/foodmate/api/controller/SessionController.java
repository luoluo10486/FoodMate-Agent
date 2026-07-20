package com.foodmate.api.controller;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController extends AuthenticatedControllerSupport {
    public SessionController(UserAccountService accounts) { super(accounts); }

    @GetMapping
    public ApiResponse<List<UserAccountService.SessionRecord>> list(jakarta.servlet.http.HttpServletRequest request) {
        return ApiResponse.success(accounts.listSessions(user(request).userId()), TraceContextHolder.currentOrNew());
    }

    @PostMapping
    public ApiResponse<UserAccountService.SessionRecord> create(jakarta.servlet.http.HttpServletRequest servletRequest, @Valid @RequestBody SessionRequest request) {
        return ApiResponse.success(accounts.createSession(user(servletRequest).userId(), request.title(), request.mode()), TraceContextHolder.currentOrNew());
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> archive(jakarta.servlet.http.HttpServletRequest request, @PathVariable long sessionId) {
        accounts.archiveSession(user(request).userId(), sessionId);
        return ApiResponse.success(null, TraceContextHolder.currentOrNew());
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<UserAccountService.MessageRecord>> messages(jakarta.servlet.http.HttpServletRequest request, @PathVariable long sessionId) {
        return ApiResponse.success(accounts.listMessages(user(request).userId(), sessionId), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/{sessionId}/messages")
    public ApiResponse<UserAccountService.MessageRecord> addMessage(jakarta.servlet.http.HttpServletRequest servletRequest, @PathVariable long sessionId, @Valid @RequestBody MessageRequest request) {
        return ApiResponse.success(accounts.addMessage(user(servletRequest).userId(), sessionId, request.role(), request.content(), request.structuredPayload()), TraceContextHolder.currentOrNew());
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SessionRequest(@NotBlank String title, String mode) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MessageRequest(@NotBlank String role, String content, Object structuredPayload) {}
}
