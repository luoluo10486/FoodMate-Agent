package com.foodmate.api.controller.food;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.api.request.food.ApprovalProposalRequest;
import com.foodmate.api.response.food.ApprovalExecuteResponse;
import com.foodmate.api.response.food.ApprovalProposalResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 写操作确认接口；业务写入只发生在 execute。 */
@RestController
@Profile("local")
@RequestMapping("/api/approvals")
public class ApprovalController extends AuthenticatedControllerSupport {
    private final ApprovalService approvals;

    public ApprovalController(UserAccountService accounts, ApprovalService approvals) {
        super(accounts);
        this.approvals = approvals;
    }

    @PostMapping("/proposals")
    public ApiResponse<ApprovalProposalResponse> propose(
            HttpServletRequest request, @Valid @RequestBody ApprovalProposalRequest body) {
        return ok(
                map(
                        approvals.propose(
                                user(request).userId(),
                                new ApprovalService.ProposalCommand(
                                        body.sessionId(),
                                        body.agentRunId(),
                                        body.operation(),
                                        body.resourceType(),
                                        body.resourceId(),
                                        body.parameters(),
                                        body.idempotencyKey(),
                                        body.expiresInSeconds()))));
    }

    @GetMapping("/{approvalRequestId}")
    public ApiResponse<ApprovalProposalResponse> get(
            HttpServletRequest request, @PathVariable long approvalRequestId) {
        return ok(map(approvals.get(user(request).userId(), approvalRequestId)));
    }

    @PostMapping("/{approvalRequestId}/confirm")
    public ApiResponse<ApprovalProposalResponse> confirm(
            HttpServletRequest request,
            @PathVariable long approvalRequestId,
            @RequestBody com.fasterxml.jackson.databind.JsonNode parameters) {
        return ok(map(approvals.confirm(user(request).userId(), approvalRequestId, parameters)));
    }

    @PostMapping("/{approvalRequestId}/reject")
    public ApiResponse<ApprovalProposalResponse> reject(
            HttpServletRequest request,
            @PathVariable long approvalRequestId,
            @RequestBody com.fasterxml.jackson.databind.JsonNode parameters) {
        return ok(map(approvals.reject(user(request).userId(), approvalRequestId, parameters)));
    }

    @PostMapping("/{approvalRequestId}/execute")
    public ApiResponse<ApprovalExecuteResponse> execute(
            HttpServletRequest request,
            @PathVariable long approvalRequestId,
            @RequestBody com.fasterxml.jackson.databind.JsonNode parameters) {
        return ok(map(approvals.execute(user(request).userId(), approvalRequestId, parameters)));
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }

    private ApprovalProposalResponse map(ApprovalService.ProposalView value) {
        return new ApprovalProposalResponse(
                Long.toString(value.approvalRequestId()),
                value.operation(),
                value.resourceType(),
                value.resourceId(),
                value.parametersDigest(),
                value.status(),
                value.expiresAt(),
                value.confirmedAt(),
                value.executedAt());
    }

    private ApprovalExecuteResponse map(ApprovalService.ExecuteView value) {
        return new ApprovalExecuteResponse(
                Long.toString(value.approvalRequestId()),
                value.operation(),
                value.status(),
                value.resourceId());
    }
}
