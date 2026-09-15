package com.company.cloud.common.result;

/**
 * 业务异常：抛出后由全局异常处理器转为统一 Result 返回。
 *
 * <p>用法：{@code throw new BizException(ErrorCode.MOVE_INTO_SUBDIR);}
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
