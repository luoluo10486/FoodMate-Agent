package com.foodmate.shared.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 后台允许执行软删除恢复的资源类型。 */
public enum RestorableResourceType {
    USER("user"),
    KNOWLEDGE_DOCUMENT("knowledge_document"),
    FOOD_LOG("food_log"),
    MEAL_PLAN("meal_plan"),
    MESSAGE("message");

    private final String code;

    RestorableResourceType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static RestorableResourceType fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "unsupported restorable resource type: " + code));
    }
}
