package com.foodmate.api.response.knowledge;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 兼容单文档上传响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DocumentUploadResponse(long documentId) {}
