package com.foodmate.api.controller.conversation;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.api.request.conversation.MemoryUpdateRequest;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.conversation.service.MemoryCandidateService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
    public ApiResponse<List<MemoryCandidateService.MemoryView>> list(HttpServletRequest request) {
        return ApiResponse.success(
                memories.list(user(request).userId()), TraceContextHolder.currentOrNew());
    }

    @PatchMapping("/{memoryId}")
    public ApiResponse<MemoryCandidateService.MemoryView> update(
            HttpServletRequest request,
            @PathVariable long memoryId,
            @Valid @RequestBody MemoryUpdateRequest body) {
        return ApiResponse.success(
                memories.update(user(request).userId(), memoryId, body.memoryValue(), body.scope()),
                TraceContextHolder.currentOrNew());
    }

    @DeleteMapping("/{memoryId}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable long memoryId) {
        memories.delete(user(request).userId(), memoryId);
        return ApiResponse.success(null, TraceContextHolder.currentOrNew());
    }

    @PostMapping("/{memoryId}/confirm")
    public ApiResponse<MemoryCandidateService.MemoryView> confirm(
            HttpServletRequest request, @PathVariable long memoryId) {
        return ApiResponse.success(
                memories.confirm(user(request).userId(), memoryId),
                TraceContextHolder.currentOrNew());
    }
}
