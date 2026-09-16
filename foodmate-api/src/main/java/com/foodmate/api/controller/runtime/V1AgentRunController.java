package com.foodmate.api.controller.runtime;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.api.request.runtime.BudgetExtensionRequest;
import com.foodmate.api.request.runtime.CancelRequest;
import com.foodmate.api.request.runtime.RecoveryRequest;
import com.foodmate.api.request.runtime.ToolSkipRequest;
import com.foodmate.api.response.runtime.RunView;
import com.foodmate.api.response.runtime.ToolSkipResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.RuntimeCancellationService;
import com.foodmate.application.runtime.service.RuntimeRecoveryService;
import com.foodmate.application.runtime.service.ToolSkipService;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AgentRun 查询、取消、预算追加和 checkpoint 恢复接口。 */
@RestController
@RequestMapping("/api/agent-runs")
public class V1AgentRunController extends AuthenticatedControllerSupport {
    private final V1RuntimeEventService events;
    private final RuntimeCancellationService cancellations;
    private final com.foodmate.application.runtime.service.BudgetExtensionService budgets;
    private final RuntimeRecoveryService recovery;
    private final ToolSkipService skips;

    public V1AgentRunController(
            UserAccountService accounts,
            V1RuntimeEventService events,
            RuntimeCancellationService cancellations,
            com.foodmate.application.runtime.service.BudgetExtensionService budgets,
            RuntimeRecoveryService recovery,
            ToolSkipService skips) {
        super(accounts);
        this.events = events;
        this.cancellations = cancellations;
        this.budgets = budgets;
        this.recovery = recovery;
        this.skips = skips;
    }

    @GetMapping("/{runId}")
    public ApiResponse<RunView> status(@PathVariable String runId, HttpServletRequest request) {
        events.requireRunOwner(runId, user(request).userId());
        var history = events.events(runId);
        return ApiResponse.success(
                new RunView(runId, events.status(runId), history.size()),
                TraceContextHolder.currentOrNew());
    }

    @PostMapping("/{runId}/cancel")
    public ApiResponse<RuntimeCancellationService.CancelResult> cancel(
            @PathVariable String runId,
            HttpServletRequest request,
            @Valid @RequestBody(required = false) CancelRequest body) {
        String reason =
                body == null || body.reason() == null || body.reason().isBlank()
                        ? "user_requested"
                        : body.reason();
        return ApiResponse.success(
                cancellations.request(user(request).userId(), runId, reason),
                TraceContextHolder.currentOrNew());
    }

    @PostMapping("/{runId}/budget-extensions")
    public ApiResponse<
                    com.foodmate.application.runtime.service.BudgetExtensionService.ExtensionResult>
            extendBudget(
                    @PathVariable String runId,
                    HttpServletRequest request,
                    @Valid @RequestBody BudgetExtensionRequest body) {
        return ApiResponse.success(
                budgets.confirm(
                        user(request).userId(),
                        Long.parseLong(runId),
                        body.additionalTokens(),
                        body.additionalCostCny(),
                        body.confirmationDigest()),
                TraceContextHolder.currentOrNew());
    }

    /**
     * 用户补参、预算确认或工具审批后，从已校验 checkpoint 恢复原 Run。
     *
     * <p>恢复不会复用旧 dispatch，而是由应用服务创建新的 dispatch attempt，并把恢复命令写入 PostgreSQL Outbox，后续仍由 Outbox Relay
     * 投递到 Python Runtime。
     */
    @PostMapping("/{runId}/recover")
    public ApiResponse<RuntimeRecoveryService.RecoveryResult> recover(
            @PathVariable String runId,
            HttpServletRequest request,
            @Valid @RequestBody RecoveryRequest body) {
        UserAccountService.UserRecord current = user(request);
        long parsedRunId;
        try {
            parsedRunId = Long.parseLong(runId);
        } catch (NumberFormatException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "run_id must be numeric");
        }
        return ApiResponse.success(
                recovery.recover(
                        new RuntimeRecoveryService.RecoveryCommand(
                                current.userId(),
                                parsedRunId,
                                body.checkpointVersion(),
                                body.checkpointDigest(),
                                body.completedInvocationIds())),
                TraceContextHolder.currentOrNew());
    }

    /**
     * Uses the checkpoint fact already accepted by Java; clients do not submit checkpoint metadata.
     */
    @PostMapping("/{runId}/recover-from-checkpoint")
    public ApiResponse<RuntimeRecoveryService.RecoveryResult> recoverFromCheckpoint(
            @PathVariable String runId, HttpServletRequest request) {
        UserAccountService.UserRecord current = user(request);
        try {
            return ApiResponse.success(
                    recovery.recoverFromPersistedCheckpoint(
                            current.userId(), Long.parseLong(runId)),
                    TraceContextHolder.currentOrNew());
        } catch (NumberFormatException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "run_id must be numeric");
        }
    }

    /** 失败 Run 的重试必须由 Java 根据已接收的 retryable 事件和 checkpoint 事实裁决。 */
    @PostMapping("/{runId}/retry")
    public ApiResponse<RuntimeRecoveryService.RecoveryResult> retry(
            @PathVariable String runId, HttpServletRequest request) {
        long parsedRunId;
        try {
            parsedRunId = Long.parseLong(runId);
        } catch (NumberFormatException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "run_id must be numeric");
        }
        return ApiResponse.success(
                recovery.retryFailedRun(user(request).userId(), parsedRunId),
                TraceContextHolder.currentOrNew());
    }

    /** 仅允许跳过仍处于 claimed 状态且注册表明确声明可跳过的工具步骤。 */
    @PostMapping("/{runId}/tool-proposals/{proposalId}/skip")
    public ApiResponse<ToolSkipResponse> skip(
            @PathVariable String runId,
            @PathVariable String proposalId,
            HttpServletRequest request,
            @Valid @RequestBody ToolSkipRequest body) {
        return ApiResponse.success(
                ToolSkipResponse.from(
                        skips.request(user(request).userId(), runId, proposalId, body.reason())),
                TraceContextHolder.currentOrNew());
    }
}
