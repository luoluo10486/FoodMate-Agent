package com.foodmate.api.controller;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.application.runtime.MemoryCandidateService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 长期记忆管理接口；只允许用户管理自己的记忆，不允许前端直接写入任意 user_id。 */
@RestController
@RequestMapping("/api/memories")
public class MemoryController extends AuthenticatedControllerSupport {
    private final MemoryCandidateService memories;

    public MemoryController(UserAccountService accounts, MemoryCandidateService memories) {
        super(accounts);
        this.memories = memories;
    }

    @GetMapping
    public ApiResponse<?> list(HttpServletRequest request) {
        return ok(memories.list(user(request).userId()));
    }

    @PatchMapping("/{memoryId}")
    public ApiResponse<?> update(
            HttpServletRequest request,
            @PathVariable long memoryId,
            @Valid @RequestBody MemoryUpdateRequest body) {
        return ok(
                memories.update(
                        user(request).userId(), memoryId, body.memoryValue(), body.scope()));
    }

    @DeleteMapping("/{memoryId}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable long memoryId) {
        memories.delete(user(request).userId(), memoryId);
        return ok(null);
    }

    @PostMapping("/{memoryId}/confirm")
    public ApiResponse<?> confirm(HttpServletRequest request, @PathVariable long memoryId) {
        return ok(memories.confirm(user(request).userId(), memoryId));
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }

    public record MemoryUpdateRequest(
            @Size(max = 4000) String memoryValue, @Size(max = 32) String scope) {}
}
