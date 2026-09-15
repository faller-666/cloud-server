package com.company.cloud.common.result;

/**
 * 全局错误码（总纲 §5：4xxxx 业务错误，5xxxx 系统错误）。
 *
 * <p>段位分配（新增错误码在此登记，改动走契约会签）：
 * <ul>
 *   <li>400xx 认证/权限</li>
 *   <li>401xx 用户/配额（A 组）</li>
 *   <li>402xx 传输（B 组）</li>
 *   <li>403xx 文件管理（C 组）</li>
 *   <li>500xx 系统错误</li>
 * </ul>
 */
public enum ErrorCode {

    // ---- 认证/权限 ----
    UNAUTHORIZED(40001, "未登录或令牌无效"),
    FORBIDDEN(40003, "无权限访问"),

    // ---- 文件管理（C 组 403xx）----
    FILE_NOT_FOUND(40304, "文件或目录不存在"),
    FILE_NAME_CONFLICT(40309, "同级存在同名文件"),
    MOVE_INTO_SUBDIR(40310, "不能移动到自身或其子目录下"),
    QUOTA_EXCEEDED(40311, "存储配额不足"),

    // ---- 系统 ----
    SYSTEM_ERROR(50000, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
