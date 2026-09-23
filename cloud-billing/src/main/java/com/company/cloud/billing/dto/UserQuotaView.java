package com.company.cloud.billing.dto;

import java.time.OffsetDateTime;

/**
 * users 表跨模块只读视图（billing/quota 查询与上传判定用）。
 */
public record UserQuotaView(
        Long id,
        String username,
        Long freeBytes,
        Long usedBytes,
        Long extraBytes,
        OffsetDateTime extraExpireAt
) {
    /** 总可用额度 = 免费额度 + 增量额度（§4.3 上传判定用） */
    public long totalAvailable() {
        return (freeBytes == null ? 0L : freeBytes) + (extraBytes == null ? 0L : extraBytes);
    }
}