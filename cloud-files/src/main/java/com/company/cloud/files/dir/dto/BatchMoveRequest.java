package com.company.cloud.files.dir.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 批量移动请求（前端多选文件/目录一次请求）。
 * 目标位置同级重名自动追加 (2)(3) 后缀；任一节点校验失败整体回滚。
 *
 * @param ids            待移动节点 ID 列表（文件/目录混合均可）
 * @param targetParentId 目标目录 ID（0 = 根目录）
 */
public record BatchMoveRequest(
        @NotEmpty(message = "ids 不能为空")
        List<Long> ids,

        @NotNull(message = "targetParentId 必填")
        Long targetParentId
) {
}
