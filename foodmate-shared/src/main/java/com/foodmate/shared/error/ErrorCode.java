package com.foodmate.shared.error;

/** 统一错误码枚举。 */
public enum ErrorCode {
    UNAUTHORIZED("UNAUTHORIZED", "未认证", 401),
    FORBIDDEN("FORBIDDEN", "无权限", 403),
    NOT_FOUND("NOT_FOUND", "资源不存在", 404),
    INVALID_ARGUMENT("INVALID_ARGUMENT", "参数错误", 400),
    CONFLICT("CONFLICT", "资源冲突", 409),
    RATE_LIMITED("RATE_LIMITED", "被限流", 429),
    TOOL_FAILED("TOOL_FAILED", "工具执行失败", 500),
    TOOL_POLICY_DENIED("TOOL_POLICY_DENIED", "工具调用被策略层拒绝", 403),
    TOOL_NOT_FOUND("TOOL_NOT_FOUND", "工具未注册", 404),
    TOOL_DISABLED("TOOL_DISABLED", "工具已禁用", 409),
    TOOL_SCHEMA_UNSUPPORTED("TOOL_SCHEMA_UNSUPPORTED", "工具 Schema 不受支持", 409),
    TOOL_SCOPE_DENIED("TOOL_SCOPE_DENIED", "工具调用范围不允许", 403),
    TOOL_EXECUTOR_UNAVAILABLE("TOOL_EXECUTOR_UNAVAILABLE", "工具执行器未配置", 503),
    TOOL_INPUT_INVALID("TOOL_INPUT_INVALID", "工具输入不符合 Schema", 400),
    SQL_CATALOG_UNAVAILABLE("SQL_CATALOG_UNAVAILABLE", "SQL Schema Catalog 不可用", 503),
    SQL_SCHEMA_DENIED("SQL_SCHEMA_DENIED", "SQL 访问的 Schema 不在授权目录中", 403),
    SQL_SENSITIVE_FIELD_DENIED("SQL_SENSITIVE_FIELD_DENIED", "SQL 访问了敏感字段", 403),
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
    MODEL_FAILED("MODEL_FAILED", "模型调用失败", 500),
    COORDINATION_UNAVAILABLE("COORDINATION_UNAVAILABLE", "系统暂时异常，请稍后重试", 503),
    RUNTIME_CAPACITY_EXCEEDED("RUNTIME_CAPACITY_EXCEEDED", "当前任务较多，请稍后重试", 429),
    RUNTIME_QUEUE_TIMEOUT("RUNTIME_QUEUE_TIMEOUT", "任务排队超时，请稍后重试", 504),
    DLQ_REPLAY_NOT_ELIGIBLE("DLQ_REPLAY_NOT_ELIGIBLE", "该死信当前不可重放", 409),
    DLQ_REPLAY_FACT_INCOMPLETE("DLQ_REPLAY_FACT_INCOMPLETE", "死信缺少安全重放事实", 409),
    DLQ_REPLAY_ACTIVE("DLQ_REPLAY_ACTIVE", "该死信已有重放任务", 409),
    RETENTION_POLICY_NOT_FOUND("RETENTION_POLICY_NOT_FOUND", "资源没有有效的数据保留策略", 409),
    RETENTION_NOT_ELIGIBLE("RETENTION_NOT_ELIGIBLE", "资源尚未达到清理条件", 409),
    RETENTION_HOLD_ACTIVE("RETENTION_HOLD_ACTIVE", "资源存在有效的法律或争议冻结", 409),
    RETENTION_REQUEST_ACTIVE("RETENTION_REQUEST_ACTIVE", "资源已有待处理的清理请求", 409),
    RETENTION_REQUEST_NOT_APPROVABLE("RETENTION_REQUEST_NOT_APPROVABLE", "清理请求当前不可审批", 409),
    RETENTION_APPROVAL_REQUIRED("RETENTION_APPROVAL_REQUIRED", "清理请求需要 superadmin 审批", 403),
    RETENTION_HOLD_NOT_FOUND("RETENTION_HOLD_NOT_FOUND", "冻结记录不存在或已释放", 404),
    AGENT_FEEDBACK_DISABLED("AGENT_FEEDBACK_DISABLED", "反馈功能暂未开启", 409),
    AGENT_FEEDBACK_INVALID("AGENT_FEEDBACK_INVALID", "反馈内容无效", 400),
    AGENT_FEEDBACK_NOT_FOUND("AGENT_FEEDBACK_NOT_FOUND", "可反馈的 Agent 回答不存在", 404),
    AGENT_FEEDBACK_ALREADY_SUBMITTED("AGENT_FEEDBACK_ALREADY_SUBMITTED", "该回答已经提交过反馈", 409),
    AGENT_FEEDBACK_CONFLICT("AGENT_FEEDBACK_CONFLICT", "反馈提交冲突", 409);

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
