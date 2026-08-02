package com.foodmate.application.runtime.service;

import java.util.List;
import java.util.Map;

/** Validates and executes the application-facing tool proposal contract. */
public interface ToolGatewayService {
    ProposalResult execute(ProposalCommand proposal);

    /** Compatibility entry point for the legacy MQ envelope. */
    ProposalResult executeLegacy(Map<String, Object> proposal);

    record ProposalResult(
            String proposalId,
            String runId,
            String status,
            String errorCode,
            List<Map<String, Object>> rows) {}

    record ProposalCommand(
            String proposalId,
            String runId,
            String proposalType,
            String schemaVersion,
            ProposalPayload payload) {}

    record ProposalPayload(String statement, String invocationId) {}
}
