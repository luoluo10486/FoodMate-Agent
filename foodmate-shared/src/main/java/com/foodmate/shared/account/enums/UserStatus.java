package com.foodmate.shared.account.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 用户账户状态。 */
public enum UserStatus {
    ACTIVE("active"),
    DISABLED("disabled"),
    LOCKED("locked");

    private final String code;

    UserStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static UserStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("unsupported user status: " + code));
    }
}
