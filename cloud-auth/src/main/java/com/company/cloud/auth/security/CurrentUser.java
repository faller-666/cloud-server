package com.company.cloud.auth.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户（对标任务书 req.user = { id, username, role }）
 * 在认证过滤器 JwtAuthFilter 中注入到 SecurityContext
 */
@Data
@AllArgsConstructor
public class CurrentUser {
    private Long id;
    private String username;
    private String role;

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}