package com.foodmate.api.response.knowledge;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Accepted asynchronous public knowledge import batch. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KnowledgeUploadBatchResponse(long batchId, String status) {}
