package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** V1 Java to Python RunCommand envelope. */
public record V1RunCommand(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("run_id") String runId,
        @JsonProperty("dispatch_id") String dispatchId,
        int attempt,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("request_hash") String requestHash,
        @JsonProperty("deadline_at") Instant deadlineAt,
        V1Message message,
        @JsonProperty("authorized_context") AuthorizedContext authorizedContext,
        @JsonProperty("runtime_options") RuntimeOptions runtimeOptions,
        @JsonProperty("recovery_context") @JsonInclude(JsonInclude.Include.NON_EMPTY)
                RecoveryContext recoveryContext) {
    public V1RunCommand {
        require(schemaVersion, "schemaVersion");
        require(runId, "runId");
        require(dispatchId, "dispatchId");
        require(requestId, "requestId");
        require(traceId, "traceId");
        require(requestHash, "requestHash");
        Objects.requireNonNull(deadlineAt, "deadlineAt");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(authorizedContext, "authorizedContext");
        Objects.requireNonNull(runtimeOptions, "runtimeOptions");
        if (!"v1".equals(schemaVersion))
            throw new IllegalArgumentException("schemaVersion must be v1");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }

    /** User message that initiated the Run. */
    public record V1Message(
            @JsonProperty("message_id") String messageId,
            String content,
            List<JsonNode> attachments) {
        public V1Message {
            require(messageId, "messageId");
            require(content, "content");
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    /** Java-authorized context snapshot; Runtime cannot expand its scope. */
    public record AuthorizedContext(
            @JsonProperty("session_id") String sessionId,
            String timezone,
            String locale,
            @JsonProperty("tool_contract_version") String toolContractVersion,
            @JsonProperty("recent_messages") List<RecentMessage> recentMessages,
            @JsonProperty("session_summary") SessionSummary sessionSummary,
            @JsonProperty("long_term_memories") List<MemoryContext> longTermMemories,
            @JsonProperty("sql_read_request") SqlReadRequest sqlReadRequest,
            @JsonProperty("knowledge_scope") String knowledgeScope) {
        public AuthorizedContext {
            recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
            longTermMemories = longTermMemories == null ? List.of() : List.copyOf(longTermMemories);
        }
    }

    /** Recent conversation item included in the authorized context. */
    public record RecentMessage(
            @JsonProperty("message_id") String messageId,
            String role,
            String content,
            @JsonProperty("sequence_no") Integer sequenceNo) {}

    /** Previously persisted summary snapshot supplied to the Runtime. */
    public record SessionSummary(
            @JsonProperty("summary_id") String summaryId,
            @JsonProperty("summary_text") String summaryText,
            @JsonProperty("key_constraints") String keyConstraints,
            @JsonProperty("covered_from_sequence") Integer coveredFromSequence,
            @JsonProperty("covered_to_sequence") Integer coveredToSequence,
            @JsonProperty("source_message_count") Integer sourceMessageCount,
            @JsonProperty("prompt_version") String promptVersion,
            @JsonProperty("content_digest") String contentDigest,
            Integer version) {}

    /** Confirmed long-term memory item allowed for this Run. */
    public record MemoryContext(
            @JsonProperty("memory_id") String memoryId,
            @JsonProperty("memory_type") String memoryType,
            @JsonProperty("memory_key") String memoryKey,
            @JsonProperty("memory_value") String memoryValue,
            BigDecimal confidence,
            String scope) {}

    /** Optional Java-authorized read-only SQL request. */
    public record SqlReadRequest(
            String statement,
            @JsonProperty("invocation_id") String invocationId,
            @JsonProperty("requires_confirmation") boolean requiresConfirmation) {}

    /** Execution limits and immutable model facts for this attempt. */
    public record RuntimeOptions(
            @JsonProperty("prompt_set_version") String promptSetVersion,
            @JsonProperty("max_steps") int maxSteps,
            @JsonProperty("stream_answer") boolean streamAnswer,
            @JsonProperty("budget_snapshot") BudgetSnapshot budgetSnapshot,
            @JsonProperty("model_snapshot") @JsonInclude(JsonInclude.Include.NON_EMPTY)
                    ModelSnapshot modelSnapshot) {
        public RuntimeOptions(
                String promptSetVersion,
                int maxSteps,
                boolean streamAnswer,
                BudgetSnapshot budgetSnapshot) {
            this(promptSetVersion, maxSteps, streamAnswer, budgetSnapshot, null);
        }
    }

    /** Non-secret model routing facts frozen when the run is accepted. */
    public record ModelSnapshot(
            String scene,
            @JsonProperty("model_type") String modelType,
            @JsonProperty("route_version") String routeVersion,
            @JsonProperty("provider_code") String providerCode,
            @JsonProperty("model_name") String modelName,
            @JsonProperty("fallback_provider_code") String fallbackProviderCode,
            @JsonProperty("fallback_model_name") String fallbackModelName,
            @JsonProperty("price_version") String priceVersion,
            @JsonProperty("input_price_per_million") BigDecimal inputPricePerMillion,
            @JsonProperty("output_price_per_million") BigDecimal outputPricePerMillion,
            @JsonProperty("budget_policy_version") String budgetPolicyVersion,
            @JsonProperty("model_timeout_ms") int modelTimeoutMs) {}

    /** Budget limits frozen for the current Run revision. */
    public record BudgetSnapshot(
            @JsonProperty("max_total_tokens") int maxTotalTokens,
            @JsonProperty("max_cost_cny") BigDecimal maxCostCny,
            @JsonProperty("max_step_retries") int maxStepRetries,
            @JsonProperty("max_replans") int maxReplans,
            @JsonProperty("max_answer_rewrites") int maxAnswerRewrites,
            @JsonProperty("max_total_steps") int maxTotalSteps,
            @JsonProperty("max_model_calls") int maxModelCalls,
            @JsonProperty("queue_timeout_seconds") int queueTimeoutSeconds,
            @JsonProperty("execution_timeout_seconds") int executionTimeoutSeconds,
            @JsonProperty("node_timeout_seconds") int nodeTimeoutSeconds,
            @JsonProperty("waiting_user_timeout_seconds") int waitingUserTimeoutSeconds,
            int revision,
            @JsonProperty("config_version") String configVersion) {}

    /** Canonical fields used to calculate the dispatch request hash. */
    public record RequestHashInput(
            @JsonProperty("schema_version") String schemaVersion,
            @JsonProperty("run_id") String runId,
            @JsonProperty("dispatch_id") String dispatchId,
            int attempt,
            @JsonProperty("deadline_at") Instant deadlineAt,
            V1Message message,
            @JsonProperty("authorized_context") AuthorizedContext authorizedContext,
            @JsonProperty("runtime_options") RuntimeOptions runtimeOptions) {}

    /** Previously completed facts used to resume an attempt idempotently. */
    public record RecoveryContext(
            @JsonProperty("previous_dispatch_id") String previousDispatchId,
            @JsonProperty("previous_attempt") int previousAttempt,
            @JsonProperty("checkpoint_version") int checkpointVersion,
            @JsonProperty("checkpoint_digest") String checkpointDigest,
            @JsonProperty("budget_revision") int budgetRevision,
            @JsonProperty("completed_invocation_ids") List<String> completedInvocationIds,
            @JsonProperty("completed_tool_results") List<V1ToolResult> completedToolResults) {
        public RecoveryContext {
            completedInvocationIds =
                    completedInvocationIds == null
                            ? List.of()
                            : List.copyOf(completedInvocationIds);
            completedToolResults =
                    completedToolResults == null ? List.of() : List.copyOf(completedToolResults);
        }
    }
}
