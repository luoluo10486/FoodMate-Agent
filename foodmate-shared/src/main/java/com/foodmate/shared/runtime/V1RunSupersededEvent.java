package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 等待中的 AgentRun 被接续 AgentRun 替代时发出的载荷。 */
public record V1RunSupersededEvent(
        @JsonProperty("superseded_by_run_id") String supersededByRunId) {}
