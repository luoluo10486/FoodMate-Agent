package com.foodmate.api.request.knowledge;

import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import jakarta.validation.constraints.NotNull;

/** 知识文档状态变更请求参数。 */
public record KnowledgeStatusRequest(@NotNull KnowledgeDocumentStatus status) {}
