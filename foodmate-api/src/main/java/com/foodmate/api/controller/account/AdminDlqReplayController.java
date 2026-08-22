package com.foodmate.api.controller.account;

import com.foodmate.api.request.account.DlqReplayRequest;
import com.foodmate.api.response.account.DlqReplayResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.RuntimeDlqReplayService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员 DLQ 重放入口；重放实际由异步 Outbox Relay 执行。 */
@RestController
@RequestMapping("/api/admin/dlq")
public class AdminDlqReplayController extends AuthenticatedControllerSupport {
    private final RuntimeDlqReplayService replay;

    public AdminDlqReplayController(UserAccountService accounts, RuntimeDlqReplayService replay) {
        super(accounts);
        this.replay = replay;
    }

    @PostMapping("/{dlqId}/replay")
    public ApiResponse<DlqReplayResponse> replay(
            @PathVariable long dlqId,
            @Valid @RequestBody DlqReplayRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.SUPERADMIN);
        RuntimeDlqReplayService.ReplayResult result =
                replay.request(
                        dlqId,
                        new RuntimeDlqReplayService.Command(
                                operator.userId(),
                                UserRole.fromCode(operator.role()),
                                idempotencyKey,
                                body.confirmed(),
                                body.confirmationDigest()));
        return ApiResponse.success(
                DlqReplayResponse.from(result), TraceContextHolder.currentOrNew());
    }
}
