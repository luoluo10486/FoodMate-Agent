package com.foodmate.application.runtime.service;

import java.math.BigDecimal;

/** 用户明确确认后，为等待中的 AgentRun 增加预算。 */
public interface BudgetExtensionService {
    ExtensionResult confirm(
            long userId,
            long runId,
            int additionalTokens,
            BigDecimal additionalCost,
            String confirmationDigest);

    record ExtensionResult(
            String runId, String dispatchId, int attempt, int budgetRevision, String status) {}
}
