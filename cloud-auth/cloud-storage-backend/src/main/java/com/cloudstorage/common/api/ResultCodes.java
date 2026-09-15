package com.cloudstorage.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码约定（本组 4xxxx 段内）
 * 与前端约定的用户提示文案一一对应
 */
@Getter
@AllArgsConstructor
public enum ResultCodes {

    // ---- 认证 401xx ----
    LOGIN_FAILED(40101, "用户名或密码不正确"),
    ACCOUNT_DISABLED(40102, "账号已被禁用，请联系管理员"),
    TOKEN_INVALID(40103, "登录已过期，请重新登录"),

    // ---- 锁定 423xx ----
    ACCOUNT_LOCKED(42301, "失败次数过多，请稍后再试"),

    // ---- 权限 403xx ----
    FORBIDDEN(40301, "没有操作权限"),
    MUST_CHANGE_PASSWORD(40302, "请先修改初始密码"),

    // ---- 通用 ----
    BAD_REQUEST(40000, "请求参数错误"),
    QUOTA_TOO_LOW(42101, "目标配额不能低于当前已用量"),
    USER_NOT_FOUND(40401, "用户不存在"),
    INTERNAL_ERROR(50000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    public ApiResponse<Void> toResponse() {
        return ApiResponse.error(code, message);
    }
}