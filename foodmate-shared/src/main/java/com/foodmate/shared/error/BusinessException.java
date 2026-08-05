package com.foodmate.shared.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/** 业务异常基类，携带标准错误码和详情。 */
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final JsonNode details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), JsonNodeFactory.instance.objectNode());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, JsonNodeFactory.instance.objectNode());
    }

    public BusinessException(ErrorCode errorCode, String message, JsonNode details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? JsonNodeFactory.instance.objectNode() : details;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public JsonNode details() {
        return details;
    }
}
