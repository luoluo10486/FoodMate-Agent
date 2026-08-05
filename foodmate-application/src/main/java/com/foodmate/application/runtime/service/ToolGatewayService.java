package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Validates and executes the application-facing tool proposal contract. */
public interface ToolGatewayService {
    ProposalResult execute(ProposalCommand proposal);

    record ProposalResult(
            String proposalId,
            String runId,
            String status,
            String errorCode,
            List<JsonNode> rows) {}

    record ProposalCommand(
            String proposalId,
            String runId,
            String proposalType,
            String schemaVersion,
            ProposalPayload payload) {}

    record ProposalPayload(String statement, String invocationId) {}
}
