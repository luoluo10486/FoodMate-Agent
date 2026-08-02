package com.foodmate.application.runtime.service;

import java.math.BigDecimal;

/** Extends a waiting run's budget after an explicit user confirmation. */
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
