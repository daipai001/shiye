package com.shiye.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应结构。
 *
 * <pre>
 * { "code": 0, "message": "OK", "data": {}, "requestId": "req_..." }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(
        int code,
        String message,
        T data,
        String requestId
) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.OK.code(), ErrorCode.OK.message(), data, null);
    }

    public static Result<Void> ok() {
        return new Result<>(ErrorCode.OK.code(), ErrorCode.OK.message(), null, null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.code(), errorCode.message(), null, null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null, null);
    }

    public Result<T> withRequestId(String requestId) {
        return new Result<>(code, message, data, requestId);
    }
}
