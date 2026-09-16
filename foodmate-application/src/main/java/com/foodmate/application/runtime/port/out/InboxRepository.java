package com.foodmate.application.runtime.port.out;

/** Java 接收 Runtime 提案的持久化幂等边界。 */
public interface InboxRepository {
    /** Claims a new proposal or verifies that a redelivery has the same request hash. */
    int claim(String proposalId, String requestHash, String payload);

    /** Reads the previously accepted proposal fact, if any. */
    InboxRecord find(String proposalId);

    /** 只有仍处于 claimed 的 Proposal 才能进入 Java 工具执行阶段。 */
    int markExecuting(String proposalId);

    /** 用户跳过请求与工具执行使用同一个 CAS 状态转换。 */
    int requestSkip(String proposalId);

    /** Stores the terminal tool result for a claimed proposal. */
    int complete(String proposalId, String resultJson);

    /** Immutable proposal inbox state used for redelivery decisions. */
    record InboxRecord(
            String requestHash,
            String resultJson,
            String status,
            String runId,
            String dispatchId,
            int attempt,
            String invocationId,
            String toolName) {
        /** 保持旧版测试数据兼容。 */
        public InboxRecord(String requestHash, String resultJson, String status) {
            this(requestHash, resultJson, status, null, null, 0, null, null);
        }
    }
}
