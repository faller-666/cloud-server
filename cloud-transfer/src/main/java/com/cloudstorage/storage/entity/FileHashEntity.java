package com.cloudstorage.storage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * file_hashes 表（B 组 Owner）——秒传去重核心。
 * sha256 为主键：同内容文件全站只存一份，引用计数管理生命周期。
 */
@Entity
@Table(name = "file_hashes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileHashEntity {

    @Id
    @Column(length = 64)
    private String sha256;

    /** 实际存储的唯一对象 key。 */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** 引用计数；归 0 由清理任务物理删除 MinIO 对象。 */
    @Column(name = "ref_count", nullable = false)
    private int refCount;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}