package com.foodmate.api.request;

import com.foodmate.shared.account.KnowledgeDocumentStatus;
import jakarta.validation.constraints.NotNull;

public record KnowledgeStatusRequest(@NotNull KnowledgeDocumentStatus status) {}
