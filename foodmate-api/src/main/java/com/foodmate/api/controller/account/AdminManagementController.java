package com.foodmate.api.controller.account;

import com.foodmate.api.request.account.ToolStatusRequest;
import com.foodmate.api.request.account.UserStatusRequest;
import com.foodmate.api.response.account.RestoreResponse;
import com.foodmate.api.response.account.RevokedSessionsResponse;
import com.foodmate.api.response.account.StatusUpdateResponse;
import com.foodmate.application.account.service.AdminManagementService;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminManagementController extends AuthenticatedControllerSupport {
    private final AdminManagementService management;

    public AdminManagementController(
            UserAccountService accounts, AdminManagementService management) {
        super(accounts);
        this.management = management;
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<StatusUpdateResponse> userStatus(
            @PathVariable long id,
            @Valid @RequestBody UserStatusRequest body,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        management.updateUserStatus(
                id, body.status(), operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(new StatusUpdateResponse(true, body.status().code()));
    }

    @PostMapping("/users/{id}/sessions/revoke-all")
    public ApiResponse<RevokedSessionsResponse> revokeSessions(
            @PathVariable long id, HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        int changed =
                management.revokeSessions(
                        id, operator.userId(), TraceContextHolder.currentOrNew().traceId());
        return ok(new RevokedSessionsResponse(changed));
    }

    @PatchMapping("/tools/{name}/status")
    public ApiResponse<StatusUpdateResponse> toolStatus(
            @PathVariable String name,
            @Valid @RequestBody ToolStatusRequest body,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        management.updateToolStatus(
                name,
                body.status(),
                operator.userId(),
                TraceContextHolder.currentOrNew().traceId());
        return ok(new StatusUpdateResponse(true, body.status().code()));
    }

    @PostMapping("/resources/{type}/{id}/restore")
    public ApiResponse<RestoreResponse> restore(
            @PathVariable String type, @PathVariable String id, HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        long numericId;
        try {
            numericId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("resource id must be numeric");
        }
        management.restore(
                RestorableResourceType.fromCode(type),
                numericId,
                operator.userId(),
                TraceContextHolder.currentOrNew().traceId());
        return ok(new RestoreResponse(true));
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }
}
