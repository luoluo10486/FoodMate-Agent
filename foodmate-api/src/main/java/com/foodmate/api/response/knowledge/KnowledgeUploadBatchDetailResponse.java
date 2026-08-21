package com.foodmate.api.response.knowledge;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.knowledge.service.KnowledgeService;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KnowledgeUploadBatchDetailResponse(KnowledgeService.BatchDetail batch) {}
