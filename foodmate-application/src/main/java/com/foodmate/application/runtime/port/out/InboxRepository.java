package com.foodmate.application.runtime.port.out;

/** Java 接收 Runtime 提案的持久化幂等边界。 */
public interface InboxRepository {
    /** Claims a new proposal or verifies that a redelivery has the same request hash. */
    int claim(String proposalId, String requestHash, String payload);

    /** Reads the previously accepted proposal fact, if any. */
    InboxRecord find(String proposalId);

    /** Stores the terminal tool result for a claimed proposal. */
    int complete(String proposalId, String resultJson);

    /** Immutable proposal inbox state used for redelivery decisions. */
    record InboxRecord(String requestHash, String resultJson, String status) {}
}
