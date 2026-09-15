package com.company.cloud.files.dir.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 面包屑路径项（R-C01），从根到当前目录有序排列。
 *
 * <p>注意：此类会被 MyBatis 结果映射填充，必须是带无参构造的普通类，
 * 不能用 record。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BreadcrumbItem {
    private Long id;
    private String name;
}
