package com.company.cloud.auth.dto;

/**
 * App 检查更新——有更新时的更新详情（字段对齐 App 自动更新方案 §5.2）。
 */
public record AppUpdateInfo(
        int versionCode,
        String versionName,
        String apkUrl,
        long fileSize,
        String fileHash,
        boolean forceUpdate,
        String updateNotes) {
}