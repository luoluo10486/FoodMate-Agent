package com.foodmate.application.runtime.port.out;

import java.util.Map;

public interface InboxRepository {
    int claim(String proposalId, String requestHash, String payload);

    Map<String, Object> find(String proposalId);

    int complete(String proposalId, String resultJson);
}
