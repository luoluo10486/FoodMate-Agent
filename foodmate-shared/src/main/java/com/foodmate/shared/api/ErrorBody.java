package com.foodmate.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/** 统一错误响应体。 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorBody(String code, String message, JsonNode details) {
    public ErrorBody {
        details = details == null ? JsonNodeFactory.instance.objectNode() : details;
    }
}
