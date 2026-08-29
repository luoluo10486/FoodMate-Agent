package com.foodmate.shared.runtime;

/** Runtime 协议无法安全完成时使用的稳定错误码异常。 */
public class RuntimeException extends java.lang.RuntimeException {
    private final String code;

    public RuntimeException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Returns the stable protocol error code exposed to boundary adapters. */
    public String code() {
        return code;
    }
}
