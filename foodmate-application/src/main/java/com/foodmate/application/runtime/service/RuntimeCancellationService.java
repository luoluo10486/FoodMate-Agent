package com.foodmate.application.runtime.service;

/** 协调持久化取消请求及其向 Runtime 的交付。 */
public interface RuntimeCancellationService {
    CancelResult request(long userId, String runId, String reason);

    void publishRequested();

    record CancelResult(String runId, String status, boolean terminal) {}
}
