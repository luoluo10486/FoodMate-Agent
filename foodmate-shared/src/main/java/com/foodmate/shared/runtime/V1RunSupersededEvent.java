package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Payload emitted when a waiting run is superseded by a continuation run. */
public record V1RunSupersededEvent(
        @JsonProperty("superseded_by_run_id") String supersededByRunId) {}
