package com.foodmate.shared.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 知识文档处理状态。 */
public enum KnowledgeDocumentStatus {
    UPLOADED("uploaded"),
    PARSED("parsed"),
    INDEXED("indexed"),
    DISABLED("disabled");

    private final String code;

    KnowledgeDocumentStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static KnowledgeDocumentStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "unsupported knowledge document status: " + code));
    }
}
