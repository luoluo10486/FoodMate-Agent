package com.foodmate.application.runtime.service;

/** Coordinates durable cancellation requests and their delivery to Runtime. */
public interface RuntimeCancellationService {
    CancelResult request(long userId, String runId, String reason);

    void publishRequested();

    record CancelResult(String runId, String status, boolean terminal) {}
}
