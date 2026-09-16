package com.cloudstorage.storage.exception;

/**
 * 业务异常（携带错误码）。
 * 本应归 common 模块的统一异常体系 + @RestControllerAdvice 全局处理（由 A 组交付统一错误码表），
 * 这里先占位，后续迁到 common 的 BusinessException。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}