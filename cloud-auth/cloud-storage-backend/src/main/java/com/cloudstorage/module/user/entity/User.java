package com.cloudstorage.module.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * users 表实体（与 V1__create_users.sql 对齐，ddl-auto=validate 校验）
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