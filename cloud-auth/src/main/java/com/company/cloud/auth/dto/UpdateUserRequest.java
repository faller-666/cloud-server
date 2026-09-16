package com.company.cloud.auth.dto;

import lombok.Data;

/**
 * 管理端更新用户：禁用/启用、调整配额、变更角色（均可选）
 */
@Data
public class UpdateUserRequest {
    /** active / disabled */
    private String status;

    /** 调整后的配额（字节），不得低于当前已用量 */
    private Long quotaBytes;

    /** admin / user */
    private String role;

    /** 显示昵称（可选，V1002） */
    private String nickname;

    /** 邮箱（可选，唯一，V1002） */
    private String email;
}