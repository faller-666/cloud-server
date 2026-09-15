package com.company.cloud.files.dir.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建文件夹请求（R-C02）。
 *
 * @param parentId 父目录 ID，0 或缺省表示根目录
 * @param name     文件夹名；同级重名时服务端自动追加 (2)(3) 后缀
 */
public record MkdirRequest(
        Long parentId,

        @NotBlank(message = "文件夹名不能为空")
        @Size(max = 255, message = "文件夹名超长")
        String name
) {
    public long parentIdOrRoot() {
        return parentId == null ? 0L : parentId;
    }
}
