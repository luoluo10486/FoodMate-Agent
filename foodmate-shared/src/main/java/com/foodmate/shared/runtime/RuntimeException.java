package com.foodmate.shared.runtime;

public class RuntimeException extends java.lang.RuntimeException {
    private final String code;
    public RuntimeException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
