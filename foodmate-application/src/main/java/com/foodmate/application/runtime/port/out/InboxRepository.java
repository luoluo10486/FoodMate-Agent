package com.foodmate.application.runtime.port.out;

public interface InboxRepository {
    int claim(String proposalId, String requestHash, String payload);

    InboxRecord find(String proposalId);

    int complete(String proposalId, String resultJson);

    record InboxRecord(String requestHash, String resultJson, String status) {}
}
