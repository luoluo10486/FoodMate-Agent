package com.foodmate.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 统一错误响应体。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorBody(
        String code,
        String message,
        Map<String, Object> details
) {
}
