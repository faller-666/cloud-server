package com.company.cloud.files.dir.dto;

import com.company.cloud.files.dir.entity.FileNode;

import java.time.OffsetDateTime;

/**
 * 文件/目录节点视图（返回前端）。
 */
public record FileNodeVO(
        Long id,
        Long parentId,
        String name,
        Boolean isDir,
        Long size,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static FileNodeVO from(FileNode node) {
        return new FileNodeVO(
                node.getId(),
                node.getParentId(),
                node.getName(),
                node.getIsDir(),
                node.getSize(),
                node.getCreatedAt(),
                node.getUpdatedAt()
        );
    }
}
