package com.foodmate.api.controller.account;

import com.foodmate.api.request.account.RetentionApprovalRequest;
import com.foodmate.api.request.account.RetentionHoldRequest;
import com.foodmate.api.request.account.RetentionPurgeRequest;
import com.foodmate.api.response.account.RetentionHoldResponse;
import com.foodmate.api.response.account.RetentionPurgePreflightResponse;
import com.foodmate.api.response.account.RetentionPurgeResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.retention.service.DataRetentionService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin retention governance; approval is separate from any future purge executor. */
@RestController
@RequestMapping("/api/admin/data-retention")
public class AdminRetentionController extends AuthenticatedControllerSupport {
    private final DataRetentionService retention;

    public AdminRetentionController(UserAccountService accounts, DataRetentionService retention) {
        super(accounts);
        this.retention = retention;
    }

    @PostMapping("/purge-requests")
    public ApiResponse<RetentionPurgeResponse> requestPurge(
            @RequestBody RetentionPurgeRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        var result =
                retention.requestPurge(
                        new DataRetentionService.PurgeCommand(
                                operator.userId(),
                                UserRole.fromCode(operator.role()),
                                TraceContextHolder.currentOrNew().traceId(),
                                idempotencyKey,
                                body.resourceType(),
                                body.resourceId(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(RetentionPurgeResponse.from(result));
    }

    @GetMapping("/purge-requests/{requestId}")
    public ApiResponse<RetentionPurgeResponse> getPurge(
            @PathVariable long requestId, HttpServletRequest request) {
        requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN, UserRole.OPERATOR);
        return ok(RetentionPurgeResponse.from(retention.getPurge(requestId)));
    }

    @GetMapping("/purge-requests/{requestId}/preflight")
    public ApiResponse<RetentionPurgePreflightResponse> preflight(
            @PathVariable long requestId, HttpServletRequest request) {
        requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN, UserRole.OPERATOR);
        return ok(RetentionPurgePreflightResponse.from(retention.getPreflight(requestId)));
    }

    @PostMapping("/purge-requests/{requestId}/approve")
    public ApiResponse<RetentionPurgeResponse> approve(
            @PathVariable long requestId,
            @RequestBody RetentionApprovalRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.SUPERADMIN);
        var result =
                retention.approvePurge(
                        requestId,
                        new DataRetentionService.ApprovalCommand(
                                operator.userId(),
                                UserRole.SUPERADMIN,
                                TraceContextHolder.currentOrNew().traceId(),
                                idempotencyKey,
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(RetentionPurgeResponse.from(result));
    }

    @PostMapping("/holds")
    public ApiResponse<RetentionHoldResponse> placeHold(
            @RequestBody RetentionHoldRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        var result =
                retention.placeHold(
                        new DataRetentionService.HoldCommand(
                                operator.userId(),
                                UserRole.fromCode(operator.role()),
                                TraceContextHolder.currentOrNew().traceId(),
                                idempotencyKey,
                                body.resourceType(),
                                body.resourceId(),
                                body.reasonCode(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(RetentionHoldResponse.from(result));
    }

    @PostMapping("/holds/{holdId}/release")
    public ApiResponse<RetentionHoldResponse> releaseHold(
            @PathVariable long holdId,
            @RequestBody RetentionApprovalRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.SUPERADMIN);
        var result =
                retention.releaseHold(
                        holdId,
                        new DataRetentionService.ReleaseCommand(
                                operator.userId(),
                                UserRole.SUPERADMIN,
                                TraceContextHolder.currentOrNew().traceId(),
                                idempotencyKey,
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(RetentionHoldResponse.from(result));
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }
}
