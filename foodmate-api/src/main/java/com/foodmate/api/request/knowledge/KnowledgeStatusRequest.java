package com.foodmate.api.request.knowledge;

import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import jakarta.validation.constraints.NotNull;

public record KnowledgeStatusRequest(@NotNull KnowledgeDocumentStatus status) {}
