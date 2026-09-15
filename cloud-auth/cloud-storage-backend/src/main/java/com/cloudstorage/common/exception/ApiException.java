package com.cloudstorage.common.exception;

import com.cloudstorage.common.api.ResultCodes;
import lombok.Getter;

/**
 * 业务异常：携带 4xxxx 错误码，由全局异常处理器统一转为 ApiResponse
 */
@Getter
public class ApiException extends RuntimeException {
    private final int code;

    public ApiException(ResultCodes rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }

    public ApiException(ResultCodes rc, String detail) {
        super(detail != null ? detail : rc.getMessage());
        this.code = rc.getCode();
    }

    public static ApiException of(ResultCodes rc) {
        return new ApiException(rc);
    }
}