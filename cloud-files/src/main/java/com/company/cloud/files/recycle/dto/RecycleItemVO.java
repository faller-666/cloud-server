package com.company.cloud.files.recycle.dto;

import com.company.cloud.files.dir.entity.FileNode;

import java.time.OffsetDateTime;

/**
 * 回收站条目视图（返回前端）。比 FileNodeVO 多 deletedAt / expireAt，
 * 其中 expireAt 由 RetentionPolicy 计算（当前为删除后 30 天）。
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
    /** 回收站保留期：删除后 30 天到期（产品约定值，后续可抽配置） */
    public static final long RETENTION_DAYS = 30;

    public static RecycleItemVO from(FileNode node) {
        OffsetDateTime deletedAt = node.getDeletedAt();
        OffsetDateTime expireAt = deletedAt == null ? null : deletedAt.plusDays(RETENTION_DAYS);
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
