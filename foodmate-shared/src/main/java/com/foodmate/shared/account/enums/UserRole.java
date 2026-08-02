package com.foodmate.shared.account.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 用户权限角色。持久化和 HTTP 契约使用小写 code。 */
public enum UserRole {
    USER("user"),
    OPERATOR("operator"),
    ADMIN("admin"),
    SUPERADMIN("superadmin");

    private final String code;

    UserRole(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static UserRole fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported user role: " + code));
    }
}
