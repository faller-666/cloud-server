package com.company.cloud.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * app_release 表实体（与 V2009__create_app_release.sql 对齐，ddl-auto=validate 校验）。
 *
 * <p>App(android/ios)/EXE(windows) 共用一张表，靠 {@code platform} 字段区分。
 */
@Entity
@Table(name = "app_release")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 平台：android / ios / windows */
    @Column(nullable = false)
    private String platform;

    /** 构建版本号，同平台递增唯一 */
    @Column(name = "version_code", nullable = false)
    private Integer versionCode;

    /** 展示版本，如 1.3.0 */
    @Column(name = "version_name", nullable = false)
    private String versionName;

    /** 下载地址 */
    @Column(name = "apk_url", nullable = false)
    private String apkUrl;

    /** 包体大小（字节） */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** 安装包 SHA-256（小写 hex） */
    @Column(name = "file_hash", nullable = false)
    private String fileHash;

    /** 更新说明（多行） */
    @Column(name = "update_notes")
    private String updateNotes;

    /** 是否强制更新 */
    @Column(name = "force_update", nullable = false)
    private Boolean forceUpdate = false;

    /** 全局最低强制版本：低于此版本一律强制，0=不使用 */
    @Column(name = "min_force_code", nullable = false)
    private Integer minForceCode = 0;

    /** 灰度比例 0~100，100=全量 */
    @Column(name = "rollout_percent", nullable = false)
    private Integer rolloutPercent = 100;

    /** 状态：draft / published / disabled */
    @Column(nullable = false)
    private String status = "draft";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}