package com.company.cloud.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * users 表实体（与 V1__create_users.sql 对齐，ddl-auto=validate 校验）
 *
 * <p>P2 字段补充（V1002）：nickname / email / last_login_at，对应用户资料展示。
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role = "user";

    @Column(name = "quota_bytes", nullable = false)
    private Long quotaBytes = 21474836480L; // 默认 20GB

    @Column(name = "used_bytes", nullable = false)
    private Long usedBytes = 0L;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = true;

    /** 显示昵称（可选，V1002） */
    private String nickname;

    /** 邮箱（可选，唯一，V1002） */
    private String email;

    /** 最近登录时间（登录成功时回填，V1002） */
    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public boolean isAdmin() {
        return "admin".equals(this.role);
    }

    public boolean isDisabled() {
        return "disabled".equals(this.status);
    }
}