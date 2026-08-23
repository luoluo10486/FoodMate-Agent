package com.foodmate.shared.runtime;

/** Stable-code exception used when the Runtime protocol cannot be completed safely. */
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
