package com.company.cloud.common.result;

import java.io.Serializable;

/**
 * 统一返回格式（总纲 §5 全局约定，契约改动须三组会签）。
 *
 * <p>code = 0 成功；4xxxx 业务错误；5xxxx 系统错误。
 *
 * @param <T> 业务数据类型
 */
public record Result<T>(int code, String message, T data) implements Serializable {

    public static <T> Result<T> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }
}
