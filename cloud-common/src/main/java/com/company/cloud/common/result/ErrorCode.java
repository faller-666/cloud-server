package com.company.cloud.common.result;

/**
 * 全局错误码（总纲 §5：4xxxx 业务错误，5xxxx 系统错误）。
 *
 * <p>段位分配（新增错误码在此登记，改动走契约会签）：
 * <ul>
 *   <li>400xx 通用请求错误</li>
 *   <li>401xx/40301-40302/40401/42101/42301 认证与用户（A 组，任务书 02 契约原值）</li>
 *   <li>402xx 传输（B 组）</li>
 *   <li>403xx 文件管理（C 组）</li>
 *   <li>500xx 系统错误</li>
 * </ul>
 */
public enum ErrorCode {

    // ---- 认证/权限 ----
    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40001, "未登录或令牌无效"),

    // ---- 认证/用户（A 组，码值对齐任务书 02 契约，前端按此映射提示）----
    LOGIN_FAILED(40101, "用户名或密码错误"),
    ACCOUNT_DISABLED(40102, "账号已被禁用"),
    TOKEN_INVALID(40103, "登录已过期，请重新登录"),
    FORBIDDEN(40301, "无权限访问"),
    MUST_CHANGE_PASSWORD(40302, "请先修改初始密码"),
    USER_NOT_FOUND(40401, "用户不存在"),
    QUOTA_TOO_LOW(42101, "配额不能低于当前已用量"),
    ACCOUNT_LOCKED(42301, "失败次数过多，账号已临时锁定"),

    // ---- 传输（B 组 402xx）----
    PART_INDEX_OUT_OF_RANGE(40201, "分片序号越界"),
    PART_SIZE_EXCEEDED(40202, "分片大小超过上限"),
    FILE_SIZE_INVALID(40203, "文件大小非法"),
    FILE_TOO_LARGE(40204, "文件过大，单次最大支持 10GB"),
    EXTENSION_NOT_ALLOWED(40205, "该类型文件不允许上传"),
    UPLOAD_SESSION_NOT_FOUND(40206, "上传会话不存在或已过期"),
    PARTS_INCOMPLETE(40207, "分片未传完整，请继续上传"),
    STORAGE_QUOTA_EXCEEDED(40208, "存储额度已满，请申请增额"),
    SHA256_REQUIRED(40209, "缺少文件哈希（sha256），无法初始化上传"),
    UPLOAD_SESSION_EXPIRED(40210, "上传会话已失效，请重新上传"),
    UPLOAD_SESSION_IN_PROGRESS(40211, "任务正在上传中，请先放弃"),

    // ---- 文件管理（C 组 403xx）----
    FILE_NOT_FOUND(40304, "文件或目录不存在"),
    FILE_NAME_CONFLICT(40309, "同级存在同名文件"),
    MOVE_INTO_SUBDIR(40310, "不能移动到自身或其子目录下"),
    QUOTA_EXCEEDED(40311, "存储配额不足"),
    RECYCLE_NOT_FOUND(40314, "回收站中不存在该文件"),
    DIR_NOT_EMPTY_RESTORE_CONFLICT(40315, "还原目标位置存在同名文件"),

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