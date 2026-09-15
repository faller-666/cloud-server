package com.company.cloud.files.recycle.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子树文件清单项（彻底删除用）：回收站级联物理删除前，先收集子树内
 * 全部文件节点的 sha256 / size，用于释放配额与递减引用计数（R-C06/R-C07）。
 *
 * <p>注意：此类会被 MyBatis 结果映射填充，必须是带无参构造的普通类，
 * 不能用 record。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubtreeFile {
    private Long id;
    private String sha256;
    private Long size;
}
