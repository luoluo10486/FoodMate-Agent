package com.foodmate.application.runtime.persistence;

import java.util.Map;

public interface ProposalInboxStore {
    int claim(String proposalId, String requestHash, String payload);

    Map<String, Object> find(String proposalId);

    int complete(String proposalId, String resultJson);
}
