package com.foodmate.api.controller.conversation;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.api.request.conversation.MessageRequest;
import com.foodmate.api.request.conversation.MessageUpdateRequest;
import com.foodmate.api.request.conversation.RenameRequest;
import com.foodmate.api.request.conversation.SessionRequest;
import com.foodmate.api.response.conversation.MessageResponse;
import com.foodmate.api.response.conversation.SearchResponse;
import com.foodmate.api.response.conversation.SessionResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.conversation.service.SessionSummaryService;
import com.foodmate.application.runtime.service.AgentRunCommandService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.conversation.enums.MessageRole;
import com.foodmate.shared.conversation.enums.SessionStatus;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 会话、消息及用户会话内 AgentRun 入口。 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController extends AuthenticatedControllerSupport {
    private final AgentRunCommandService agentRuns;
    private final SessionSummaryService summaries;

    public SessionController(
            UserAccountService accounts,
            ObjectProvider<AgentRunCommandService> agentRunProvider,
            ObjectProvider<SessionSummaryService> summaryProvider) {
        super(accounts);
        this.agentRuns = agentRunProvider.getIfAvailable();
        this.summaries = summaryProvider.getIfAvailable();
    }

    @GetMapping
    public ApiResponse<UserAccountService.PageResult<SessionResponse>> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status) {
        String statusCode = status == null ? null : SessionStatus.fromCode(status).code();
        return ok(
                mapSessions(
                        accounts.listSessions(user(request).userId(), page, size, q, statusCode)));
    }

    @GetMapping("/deleted")
    public ApiResponse<UserAccountService.PageResult<SessionResponse>> deleted(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ok(mapSessions(accounts.listDeletedSessions(user(request).userId(), page, size)));
    }

    @GetMapping("/search")
    public ApiResponse<List<SearchResponse>> search(
            HttpServletRequest request,
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ok(
                accounts.searchSessions(user(request).userId(), q, page, size).stream()
                        .map(this::toSearchResponse)
                        .toList());
    }

    @PostMapping
    public ApiResponse<SessionResponse> create(
            HttpServletRequest request, @Valid @RequestBody SessionRequest body) {
        return ok(
                toSessionResponse(
                        accounts.createSession(
                                user(request).userId(),
                                body.title(),
                                body.mode() == null ? null : body.mode().code())));
    }

    @PatchMapping("/{sessionId}")
    public ApiResponse<Void> rename(
            HttpServletRequest request,
            @PathVariable long sessionId,
            @Valid @RequestBody RenameRequest body) {
        accounts.renameSession(user(request).userId(), sessionId, body.title());
        return ok(null);
    }

    @PostMapping("/{sessionId}/archive")
    public ApiResponse<Void> archive(HttpServletRequest request, @PathVariable long sessionId) {
        accounts.setSessionStatus(user(request).userId(), sessionId, SessionStatus.ARCHIVED.code());
        return ok(null);
    }

    @PostMapping("/{sessionId}/unarchive")
    public ApiResponse<Void> unarchive(HttpServletRequest request, @PathVariable long sessionId) {
        accounts.setSessionStatus(user(request).userId(), sessionId, SessionStatus.ACTIVE.code());
        return ok(null);
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable long sessionId) {
        accounts.deleteSession(user(request).userId(), sessionId);
        return ok(null);
    }

    @PostMapping("/{sessionId}/restore")
    public ApiResponse<Void> restore(HttpServletRequest request, @PathVariable long sessionId) {
        accounts.restoreSession(user(request).userId(), sessionId);
        return ok(null);
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<UserAccountService.PageResult<MessageResponse>> messages(
            HttpServletRequest request,
            @PathVariable long sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ok(
                mapMessages(accounts.listMessages(user(request).userId(), sessionId, page, size)));
    }

    @PostMapping("/{sessionId}/messages")
    public ApiResponse<MessageResponse> addMessage(
            HttpServletRequest request,
            @PathVariable long sessionId,
            @Valid @RequestBody MessageRequest body) {
        if (body.role() != MessageRole.USER)
            throw new IllegalArgumentException("only role=user is accepted");
        var current = user(request);
        if (agentRuns == null)
            return ok(
                    toMessageResponse(
                            accounts.addMessage(
                                    current.userId(),
                                    sessionId,
                                    body.role().code(),
                                    body.content(),
                                    body.structuredPayload())));
        return ok(
                toMessageResponse(
                        agentRuns.createUserMessageRun(
                                current.userId(),
                                sessionId,
                                body.content(),
                                TraceContextHolder.currentOrNew().traceId())));
    }

    @PatchMapping("/{sessionId}/messages/{messageId}")
    public ApiResponse<MessageResponse> updateMessage(
            HttpServletRequest request,
            @PathVariable long sessionId,
            @PathVariable long messageId,
            @Valid @RequestBody MessageUpdateRequest body) {
        long userId = user(request).userId();
        UserAccountService.MessageRecord result =
                accounts.updateMessage(userId, sessionId, messageId, body.content());
        if (summaries != null) summaries.invalidate(userId, sessionId);
        return ok(toMessageResponse(result));
    }

    @DeleteMapping("/{sessionId}/messages/{messageId}")
    public ApiResponse<Void> deleteMessage(
            HttpServletRequest request,
            @PathVariable long sessionId,
            @PathVariable long messageId) {
        long userId = user(request).userId();
        accounts.deleteMessage(userId, sessionId, messageId);
        if (summaries != null) summaries.invalidate(userId, sessionId);
        return ok(null);
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }

    private UserAccountService.PageResult<SessionResponse> mapSessions(
            UserAccountService.PageResult<UserAccountService.SessionRecord> page) {
        return new UserAccountService.PageResult<>(
                page.items().stream().map(this::toSessionResponse).toList(),
                page.total(),
                page.page(),
                page.size());
    }

    private UserAccountService.PageResult<MessageResponse> mapMessages(
            UserAccountService.PageResult<UserAccountService.MessageRecord> page) {
        return new UserAccountService.PageResult<>(
                page.items().stream().map(this::toMessageResponse).toList(),
                page.total(),
                page.page(),
                page.size());
    }

    private SessionResponse toSessionResponse(UserAccountService.SessionRecord value) {
        return new SessionResponse(
                Long.toString(value.sessionId()),
                Long.toString(value.userId()),
                value.title(),
                value.mode(),
                value.status(),
                value.lastMessageAt());
    }

    private MessageResponse toMessageResponse(UserAccountService.MessageRecord value) {
        return new MessageResponse(
                Long.toString(value.messageId()),
                Long.toString(value.sessionId()),
                value.agentRunId() == null ? null : Long.toString(value.agentRunId()),
                value.role(),
                value.content(),
                value.structuredPayload(),
                value.sequenceNo(),
                value.createdAt());
    }

    private SearchResponse toSearchResponse(UserAccountService.SearchResult value) {
        return new SearchResponse(Long.toString(value.sessionId()), value.title(), value.snippet());
    }
}
