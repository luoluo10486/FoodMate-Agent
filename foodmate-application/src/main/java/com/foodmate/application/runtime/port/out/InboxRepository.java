package com.foodmate.application.runtime.port.out;

/** Durable idempotency boundary for Runtime proposals received by Java. */
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
