package com.foodmate.api.controller.account;

import com.foodmate.api.request.account.AdminMutationRequest;
import com.foodmate.api.request.account.ToolStatusRequest;
import com.foodmate.api.request.account.UserStatusRequest;
import com.foodmate.api.response.account.RestoreResponse;
import com.foodmate.api.response.account.RevokedSessionsResponse;
import com.foodmate.api.response.account.StatusUpdateResponse;
import com.foodmate.application.account.service.AdminManagementService;
import com.foodmate.application.account.service.AdminManagementService.AdminWriteCommand;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台管理写接口；请求幂等键由 HTTP Header 传入，业务校验委托给 application。 */
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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        var result =
                management.updateUserStatus(
                        id,
                        body.status(),
                        command(
                                operator,
                                idempotencyKey,
                                body.revision(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(new StatusUpdateResponse(result.changed(), result.status(), result.revision()));
    }

    @PostMapping("/users/{id}/sessions/revoke-all")
    public ApiResponse<RevokedSessionsResponse> revokeSessions(
            @PathVariable long id,
            @Valid @RequestBody AdminMutationRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        var result =
                management.revokeSessions(
                        id,
                        command(
                                operator,
                                idempotencyKey,
                                body.revision(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(new RevokedSessionsResponse(result.affected(), result.revision()));
    }

    @PatchMapping("/tools/{name}/status")
    public ApiResponse<StatusUpdateResponse> toolStatus(
            @PathVariable String name,
            @Valid @RequestBody ToolStatusRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        var result =
                management.updateToolStatus(
                        name,
                        body.status(),
                        command(
                                operator,
                                idempotencyKey,
                                body.revision(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(new StatusUpdateResponse(result.changed(), result.status(), result.revision()));
    }

    @PostMapping("/resources/{type}/{id}/restore")
    public ApiResponse<RestoreResponse> restore(
            @PathVariable String type,
            @PathVariable String id,
            @Valid @RequestBody AdminMutationRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        long numericId;
        try {
            numericId = Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("resource id must be numeric");
        }
        var result =
                management.restore(
                        RestorableResourceType.fromCode(type),
                        numericId,
                        command(
                                operator,
                                idempotencyKey,
                                body.revision(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(new RestoreResponse(result.changed(), result.revision()));
    }

    private AdminWriteCommand command(
            UserAccountService.UserRecord operator,
            String idempotencyKey,
            long revision,
            boolean confirmed,
            String confirmationDigest) {
        return new AdminWriteCommand(
                operator.userId(),
                UserRole.fromCode(operator.role()),
                TraceContextHolder.currentOrNew().traceId(),
                idempotencyKey,
                revision,
                confirmed,
                confirmationDigest);
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }
}
