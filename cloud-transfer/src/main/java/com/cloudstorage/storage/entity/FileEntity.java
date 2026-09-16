package com.cloudstorage.storage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * files 表（B 组 Owner）。
 * 索引见 migration V2（idx_files_dir / idx_files_trash），不在 Entity 重复声明。
 */
@Entity
@Table(name = "files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** 父目录 id，NULL 为根。 */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_dir", nullable = false)
    private boolean isDir;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    private String mime;

    /** 文件级哈希，关联 file_hashes（秒传去重）。 */
    @Column(length = 64)
    private String sha256;

    /** MinIO 对象 key。 */
    @Column(name = "storage_key")
    private String storageKey;

    /** 回收站软删标记（C 组维护），非空即已入回收站。 */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}