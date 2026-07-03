package com.foodmate.shared.error;

/**
 * 统一错误码枚举。
 */
public enum ErrorCode {
    UNAUTHORIZED("UNAUTHORIZED", "未认证", 401),
    FORBIDDEN("FORBIDDEN", "无权限", 403),
    NOT_FOUND("NOT_FOUND", "资源不存在", 404),
    INVALID_ARGUMENT("INVALID_ARGUMENT", "参数错误", 400),
    CONFLICT("CONFLICT", "资源冲突", 409),
    RATE_LIMITED("RATE_LIMITED", "被限流", 429),
    TOOL_FAILED("TOOL_FAILED", "工具执行失败", 500),
    TOOL_POLICY_DENIED("TOOL_POLICY_DENIED", "工具调用被策略层拒绝", 403),
    RAG_EMPTY("RAG_EMPTY", "检索为空", 404),
    AGENT_TIMEOUT("AGENT_TIMEOUT", "Agent 超时", 504),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统异常", 500),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "用户名或密码错误", 401),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "Access Token 已过期", 401),
    AUTH_REFRESH_TOKEN_INVALID("AUTH_REFRESH_TOKEN_INVALID", "Refresh Token 无效、过期或已撤销", 401),
    AUTH_ACCOUNT_DISABLED("AUTH_ACCOUNT_DISABLED", "账号被禁用", 403),
    AUTH_ACCOUNT_LOCKED("AUTH_ACCOUNT_LOCKED", "账号被锁定", 403),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "已登录但无权限", 403),
    AUTH_REQUIRED("AUTH_REQUIRED", "未登录", 401),
    API_VALIDATION_FAILED("API_VALIDATION_FAILED", "接口参数校验失败", 400),
    APP_BUSINESS_ERROR("APP_BUSINESS_ERROR", "业务处理失败", 400),
    RAG_FAILED("RAG_FAILED", "检索处理失败", 500),
    SQL_GUARD_DENIED("SQL_GUARD_DENIED", "SQL Guard 拒绝执行", 403),
    MODEL_FAILED("MODEL_FAILED", "模型调用失败", 500);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    ErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
