package com.foodmate.api.controller;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController extends AuthenticatedControllerSupport {
    public SessionController(UserAccountService accounts) { super(accounts); }

    @GetMapping
    public ApiResponse<UserAccountService.PageResult<UserAccountService.SessionRecord>> list(HttpServletRequest request,
                                                                                              @RequestParam(defaultValue = "1") int page,
                                                                                              @RequestParam(defaultValue = "50") int size,
                                                                                              @RequestParam(required = false) String q,
                                                                                              @RequestParam(required = false) String status) {
        return ok(accounts.listSessions(user(request).userId(), page, size, q, status));
    }

    @GetMapping("/deleted")
    public ApiResponse<UserAccountService.PageResult<UserAccountService.SessionRecord>> deleted(HttpServletRequest request,
                                                                                                 @RequestParam(defaultValue = "1") int page,
                                                                                                 @RequestParam(defaultValue = "50") int size) {
        return ok(accounts.listDeletedSessions(user(request).userId(), page, size));
    }

    @GetMapping("/search")
    public ApiResponse<List<UserAccountService.SearchResult>> search(HttpServletRequest request,
                                                                      @RequestParam String q,
                                                                      @RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "50") int size) {
        return ok(accounts.searchSessions(user(request).userId(), q, page, size));
    }

    @PostMapping
    public ApiResponse<UserAccountService.SessionRecord> create(HttpServletRequest request, @Valid @RequestBody SessionRequest body) {
        return ok(accounts.createSession(user(request).userId(), body.title(), body.mode()));
    }

    @PatchMapping("/{sessionId}")
    public ApiResponse<Void> rename(HttpServletRequest request, @PathVariable long sessionId, @Valid @RequestBody RenameRequest body) {
        accounts.renameSession(user(request).userId(), sessionId, body.title());
        return ok(null);
    }

    @PostMapping("/{sessionId}/archive")
    public ApiResponse<Void> archive(HttpServletRequest request, @PathVariable long sessionId) {
        accounts.setSessionStatus(user(request).userId(), sessionId, "archived");
        return ok(null);
    }

    @PostMapping("/{sessionId}/unarchive")
    public ApiResponse<Void> unarchive(HttpServletRequest request, @PathVariable long sessionId) {
        accounts.setSessionStatus(user(request).userId(), sessionId, "active");
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
    public ApiResponse<UserAccountService.PageResult<UserAccountService.MessageRecord>> messages(HttpServletRequest request,
                                                                                                  @PathVariable long sessionId,
                                                                                                  @RequestParam(defaultValue = "1") int page,
                                                                                                  @RequestParam(defaultValue = "100") int size) {
        return ok(accounts.listMessages(user(request).userId(), sessionId, page, size));
    }

    @PostMapping("/{sessionId}/messages")
    public ApiResponse<UserAccountService.MessageRecord> addMessage(HttpServletRequest request,
                                                                     @PathVariable long sessionId,
                                                                     @Valid @RequestBody MessageRequest body) {
        if (!"user".equals(body.role())) throw new IllegalArgumentException("only role=user is accepted");
        return ok(accounts.addMessage(user(request).userId(), sessionId, body.role(), body.content(), body.structuredPayload()));
    }

    private <T> ApiResponse<T> ok(T value) { return ApiResponse.success(value, TraceContextHolder.currentOrNew()); }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SessionRequest(String title, String mode) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RenameRequest(@NotBlank @Size(max = 255) String title) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MessageRequest(@NotBlank String role, @NotBlank @Size(max = 10000) String content, Object structuredPayload) {}
}
