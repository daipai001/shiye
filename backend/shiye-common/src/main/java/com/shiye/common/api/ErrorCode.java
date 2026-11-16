package com.shiye.common.api;

/**
 * 统一错误码。
 *
 * <p>与 docs/api.md 的 1.8 错误码表保持一致；新增错误码必须同步文档。</p>
 */
public enum ErrorCode {

    OK(0, "OK", 200),

    VALIDATION_ERROR(40001, "参数错误", 400),
    INVALID_URL(40002, "URL 非法或不安全", 400),
    FILE_TOO_LARGE(40003, "文件过大", 400),
    UNSUPPORTED_FILE_TYPE(40004, "文件类型不支持", 400),
    INVALID_STATUS_TRANSITION(40005, "状态流转非法", 400),

    UNAUTHORIZED(40101, "未登录或登录已失效", 401),
    TOKEN_EXPIRED(40102, "登录已过期", 401),
    INVALID_TOKEN_AUDIENCE(40103, "令牌类型不匹配", 401),
    REFRESH_REVOKED(40104, "刷新令牌已失效", 401),
    INVALID_AUTH_CODE(40105, "授权码无效或已过期", 401),

    FORBIDDEN(40301, "没有权限", 403),
    USER_DISABLED(40302, "账号已被禁用", 403),
    ADMIN_DISABLED(40303, "管理员账号已被禁用", 403),

    NOT_FOUND(40401, "资源不存在", 404),
    ARTICLE_NOT_FOUND(40402, "资料不存在", 404),
    CONVERSATION_NOT_FOUND(40403, "会话不存在", 404),
    TASK_NOT_FOUND(40404, "任务不存在", 404),

    CONFLICT(40901, "资源冲突或重复", 409),
    IDEMPOTENCY_CONFLICT(40902, "幂等键冲突", 409),
    IMPORT_ALREADY_RUNNING(40903, "导入任务正在运行", 409),

    RATE_LIMITED(42901, "请求过于频繁", 429),
    USER_QUOTA_EXCEEDED(42902, "个人配额已用完", 429),
    GLOBAL_BUDGET_EXCEEDED(42903, "全局预算已用完", 429),
    DAILY_TOKEN_LIMIT(42904, "今日 token 已用完", 429),

    INTERNAL_ERROR(50000, "服务异常", 500),
    DB_ERROR(50001, "数据库异常", 500),
    QUEUE_ERROR(50002, "队列异常", 500),

    AI_UNAVAILABLE(50201, "AI 服务暂不可用", 502),
    AI_TIMEOUT(50202, "AI 服务超时", 502),
    EMBEDDING_FAILED(50203, "向量化失败", 502),
    PARSE_FAILED(50204, "内容解析失败", 502),
    VECTOR_STORE_UNAVAILABLE(50205, "向量库暂不可用", 502),

    MAINTENANCE(50301, "服务维护中", 503);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
