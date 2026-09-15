package com.company.cloud.files.dir.dto;

import java.util.List;

/**
 * 目录列表响应（R-C01）：分页数据 + 面包屑路径。
 *
 * @param total      当前目录下总条数
 * @param list       当前页节点
 * @param breadcrumb 面包屑（根目录时为空数组）
 */
public record DirListResponse(
        long total,
        List<FileNodeVO> list,
        List<BreadcrumbItem> breadcrumb
) {
}
