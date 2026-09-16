package com.company.cloud.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 管理端创建用户：用户名、初始密码（可随机生成）、初始配额（默认 20GB）
 */
@Data
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 初始密码；为空则系统随机生成 */
    private String initialPassword;

    /** 初始配额（字节）；为空则使用默认 20GB */
    private Long quotaBytes;

    /** 角色，默认 user */
    private String role;

    /** 显示昵称（可选，V1002） */
    private String nickname;

    /** 邮箱（可选，唯一，V1002） */
    private String email;
}