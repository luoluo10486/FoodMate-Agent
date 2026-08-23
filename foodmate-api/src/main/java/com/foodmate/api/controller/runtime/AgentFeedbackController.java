package com.foodmate.api.controller.runtime;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.api.request.runtime.AgentFeedbackRequest;
import com.foodmate.api.response.runtime.AgentFeedbackResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.AgentFeedbackService;
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

/** 用户 Agent 回答反馈入口；身份和回答归属由服务端校验。 */
@RestController
@RequestMapping("/api/agent-runs")
public class AgentFeedbackController extends AuthenticatedControllerSupport {
    private final AgentFeedbackService feedback;

    public AgentFeedbackController(UserAccountService accounts, AgentFeedbackService feedback) {
        super(accounts);
        this.feedback = feedback;
    }

    @PostMapping("/{runId}/messages/{messageId}/feedback")
    public ApiResponse<AgentFeedbackResponse> submit(
            @PathVariable long runId,
            @PathVariable long messageId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request,
            @Valid @RequestBody AgentFeedbackRequest body) {
        long userId = user(request).userId();
        AgentFeedbackService.FeedbackResult result =
                feedback.submit(
                        userId,
                        runId,
                        messageId,
                        new AgentFeedbackService.SubmitCommand(
                                body.helpful(),
                                body.reasonCodes(),
                                body.comment(),
                                idempotencyKey));
        return ApiResponse.success(map(result), TraceContextHolder.currentOrNew());
    }

    private AgentFeedbackResponse map(AgentFeedbackService.FeedbackResult value) {
        return new AgentFeedbackResponse(
                Long.toString(value.feedbackId()),
                Long.toString(value.runId()),
                Long.toString(value.messageId()),
                value.helpful(),
                value.reasonCodes(),
                value.highRisk(),
                value.idempotencyKey());
    }
}
