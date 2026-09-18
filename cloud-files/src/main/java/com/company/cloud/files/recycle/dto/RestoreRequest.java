package com.company.cloud.files.recycle.dto;

/**
 * 还原请求体（R-C06）：可选 targetParentId，指定恢复到哪个存活目录。
 * 0 表示根目录「全部文件」；null（body 为空）表示回原位置（父目录不可用则落根）。
 */
public record RestoreRequest(Long targetParentId) {
}