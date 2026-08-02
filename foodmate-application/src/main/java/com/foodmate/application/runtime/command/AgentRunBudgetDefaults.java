package com.foodmate.application.runtime.command;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 新 Run 接受时固化的预算与超时默认值；环境变量名与配置指南保持一致。 变更环境变量只影响之后新接受的 Run，正在执行的 Run 继续使用原快照。 */
@Component
public class AgentRunBudgetDefaults {
    private final int maxTotalTokens;
    private final BigDecimal maxCostCny;
    private final int maxStepRetries;
    private final int maxReplans;
    private final int maxAnswerRewrites;
    private final int maxTotalSteps;
    private final int maxModelCalls;
    private final int queueTimeoutSeconds;
    private final int executionTimeoutSeconds;
    private final int nodeTimeoutSeconds;
    private final int waitingUserTimeoutSeconds;
    private final String configVersion;

    public AgentRunBudgetDefaults(
            @Value("${FOODMATE_AGENT_MAX_TOKENS_PER_RUN:30000}") int maxTotalTokens,
            @Value("${FOODMATE_AGENT_MAX_COST_CNY_PER_RUN:0.50}") BigDecimal maxCostCny,
            @Value("${FOODMATE_AGENT_MAX_STEP_RETRIES:2}") int maxStepRetries,
            @Value("${FOODMATE_AGENT_MAX_REPLANS:1}") int maxReplans,
            @Value("${FOODMATE_AGENT_MAX_ANSWER_REWRITES:1}") int maxAnswerRewrites,
            @Value("${FOODMATE_AGENT_MAX_TOTAL_STEPS:30}") int maxTotalSteps,
            @Value("${FOODMATE_AGENT_MAX_MODEL_CALLS:12}") int maxModelCalls,
            @Value("${FOODMATE_AGENT_QUEUE_TIMEOUT_SECONDS:30}") int queueTimeoutSeconds,
            @Value("${FOODMATE_AGENT_EXECUTION_TIMEOUT_SECONDS:120}") int executionTimeoutSeconds,
            @Value("${FOODMATE_AGENT_NODE_TIMEOUT_SECONDS:30}") int nodeTimeoutSeconds,
            @Value("${FOODMATE_AGENT_WAITING_USER_TIMEOUT_SECONDS:86400}")
                    int waitingUserTimeoutSeconds,
            @Value("${foodmate.agent.budget.config-version:m1-4-default}") String configVersion) {
        if (maxTotalTokens <= 0 || maxTotalSteps <= 0 || maxModelCalls <= 0) {
            throw new IllegalStateException("agent budget totals must be positive");
        }
        if (maxStepRetries < 0 || maxReplans < 0 || maxAnswerRewrites < 0) {
            throw new IllegalStateException("agent budget retries must be non-negative");
        }
        if (maxCostCny == null || maxCostCny.signum() <= 0) {
            throw new IllegalStateException("agent cost budget must be positive");
        }
        if (queueTimeoutSeconds <= 0
                || executionTimeoutSeconds <= 0
                || nodeTimeoutSeconds <= 0
                || waitingUserTimeoutSeconds <= 0) {
            throw new IllegalStateException("agent timeouts must be positive");
        }
        this.maxTotalTokens = maxTotalTokens;
        this.maxCostCny = maxCostCny;
        this.maxStepRetries = maxStepRetries;
        this.maxReplans = maxReplans;
        this.maxAnswerRewrites = maxAnswerRewrites;
        this.maxTotalSteps = maxTotalSteps;
        this.maxModelCalls = maxModelCalls;
        this.queueTimeoutSeconds = queueTimeoutSeconds;
        this.executionTimeoutSeconds = executionTimeoutSeconds;
        this.nodeTimeoutSeconds = nodeTimeoutSeconds;
        this.waitingUserTimeoutSeconds = waitingUserTimeoutSeconds;
        this.configVersion = configVersion;
    }

    public int maxTotalTokens() {
        return maxTotalTokens;
    }

    public BigDecimal maxCostCny() {
        return maxCostCny;
    }

    public int maxStepRetries() {
        return maxStepRetries;
    }

    public int maxReplans() {
        return maxReplans;
    }

    public int maxAnswerRewrites() {
        return maxAnswerRewrites;
    }

    public int maxTotalSteps() {
        return maxTotalSteps;
    }

    public int maxModelCalls() {
        return maxModelCalls;
    }

    public int queueTimeoutSeconds() {
        return queueTimeoutSeconds;
    }

    public int executionTimeoutSeconds() {
        return executionTimeoutSeconds;
    }

    public int nodeTimeoutSeconds() {
        return nodeTimeoutSeconds;
    }

    public int waitingUserTimeoutSeconds() {
        return waitingUserTimeoutSeconds;
    }

    public String configVersion() {
        return configVersion;
    }
}
