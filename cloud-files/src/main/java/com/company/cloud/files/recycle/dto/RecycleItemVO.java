package com.company.cloud.files.recycle.dto;

import com.company.cloud.files.dir.entity.FileNode;

import java.time.OffsetDateTime;

/**
 * 回收站条目视图（返回前端）。比 FileNodeVO 多 deletedAt / expireAt，
 * 其中 expireAt = deletedAt + 保留期（R-C06 起读取 recycle.retention-days 配置，默认 30 天）。
 */
public record RecycleItemVO(
        Long id,
        Long parentId,
        String name,
        Boolean isDir,
        Long size,
        OffsetDateTime deletedAt,
        OffsetDateTime expireAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** 回收站保留期默认值：删除后 30 天到期（正式口径为 recycle.retention-days 配置） */
    public static final long RETENTION_DAYS = 30;

    /** 兼容旧调用：未提供保留期时按默认 30 天计算。 */
    public static RecycleItemVO from(FileNode node) {
        return from(node, RETENTION_DAYS);
    }

    /** R-C06：按配置保留期计算到期时间（与定时清理任务单一口径）。 */
    public static RecycleItemVO from(FileNode node, long retentionDays) {
        OffsetDateTime deletedAt = node.getDeletedAt();
        OffsetDateTime expireAt = deletedAt == null ? null : deletedAt.plusDays(retentionDays);
        return new RecycleItemVO(
                node.getId(),
                node.getParentId(),
                node.getName(),
                node.getIsDir(),
                node.getSize(),
                deletedAt,
                expireAt,
                node.getCreatedAt(),
                node.getUpdatedAt()
        );
    }
}
