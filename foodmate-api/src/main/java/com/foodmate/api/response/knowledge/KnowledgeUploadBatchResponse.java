package com.foodmate.api.response.knowledge;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 已接收的异步公共知识库导入批次响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KnowledgeUploadBatchResponse(long batchId, String status) {}
