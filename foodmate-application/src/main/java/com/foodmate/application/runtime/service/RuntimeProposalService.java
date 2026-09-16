package com.foodmate.application.runtime.service;

import com.foodmate.shared.runtime.V1ToolProposal;

/** 统一处理 HTTP 和 RocketMQ Proposal，保证工具执行与跳过请求使用同一状态仲裁。 */
public interface RuntimeProposalService {
    ToolGatewayService.ProposalResult execute(V1ToolProposal proposal, String rawPayload);
}
