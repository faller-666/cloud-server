package com.company.cloud.auth.dto;

/**
 * 修改发布会话（PATCH /admin/app/releases/{id}）：只传需要改的字段。
 * 不允许改 versionCode / 替换文件——换包请重新发版。
 */
public record PatchReleaseRequest(
        Integer rolloutPercent,
        Boolean forceUpdate,
        String updateNotes,
        Integer minForceCode) {
}