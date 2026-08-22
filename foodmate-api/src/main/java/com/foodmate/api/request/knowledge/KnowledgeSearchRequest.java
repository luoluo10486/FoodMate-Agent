package com.foodmate.api.request.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeSearchRequest(
        @NotBlank(message = "query must not be blank") @Size(max = 2000) String query) {}
