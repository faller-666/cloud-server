package com.cloudstorage.storage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * upload_sessions 表（B 组 Owner）——分片上传会话。
 * 断点续传状态持久化在库（不放内存），重启容器后可续。
 */
@Entity
@Table(name = "upload_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** MinIO multipart uploadId。 */
    @Column(name = "upload_id", nullable = false)
    private String uploadId;

    @Column(name = "target_parent")
    private Long targetParent;

    @Column(nullable = false)
    private String name;

    /** 文件级哈希（init 时存，complete 写 file_hashes 与秒传去重用）。 */
    @Column(length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "chunk_size", nullable = false)
    @Builder.Default
    private int chunkSize = 8 * 1024 * 1024; // 默认 8MB

    /** uploading / done / aborted。 */
    @Column(nullable = false)
    @Builder.Default
    private String status = "uploading";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** complete 成功后回填的文件 id（幂等重试时返回给前端）。 */
    @Column(name = "file_id")
    private Long fileId;
}