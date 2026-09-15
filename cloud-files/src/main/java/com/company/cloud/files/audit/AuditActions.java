package com.company.cloud.files.audit;

/**
 * 审计动作字典（任务书 04 §3 R-C08，由 C 组维护，改动需提前与 A/B 组对齐）。
 *
 * <p>覆盖范围：登录、上传、下载、删除、恢复、配额变更、用户管理操作。
 */
public final class AuditActions {

    private AuditActions() {
    }

    /** 登录（A 组调用） */
    public static final String LOGIN = "login";
    /** 登出（A 组调用） */
    public static final String LOGOUT = "logout";
    /** 上传（B 组调用） */
    public static final String UPLOAD = "upload";
    /** 下载（B 组调用） */
    public static final String DOWNLOAD = "download";
    /** 删除入回收站（C 组） */
    public static final String DELETE = "delete";
    /** 彻底删除（C 组） */
    public static final String DELETE_FORCE = "delete_force";
    /** 从回收站恢复（C 组） */
    public static final String RESTORE = "restore";
    /** 配额变更（A 组调用） */
    public static final String QUOTA_CHANGE = "quota_change";
    /** 用户管理操作（A 组调用：新建/禁用/重置密码等，细分动作放 detail） */
    public static final String USER_MANAGE = "user_manage";
}
