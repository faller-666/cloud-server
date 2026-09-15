package com.company.cloud.files.dir.dto;

import jakarta.validation.constraints.Size;

/**
 * 重命名 / 移动请求（R-C03）。两个字段至少传一个：
 * <ul>
 *   <li>只传 name：重命名</li>
 *   <li>只传 parentId：移动</li>
 *   <li>都传：移动到目标目录并改名</li>
 * </ul>
 * 目标位置同级重名时自动追加 (2)(3) 后缀；禁止移入自身子目录。
 *
 * @param name     新名称，可空
 * @param parentId 目标父目录 ID（0 = 根目录），可空
 */
public record UpdateNodeRequest(
        @Size(max = 255, message = "名称超长")
        String name,

        Long parentId
) {
    public boolean isEmpty() {
        return (name == null || name.isBlank()) && parentId == null;
    }
}
