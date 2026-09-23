package com.company.cloud.billing.dto;

import java.time.OffsetDateTime;

/**
 * 用户额度视图（§4.1 quota）。
 *
 * @param freeBytes       个人免费额度（users.quota_bytes，快照制）
 * @param extraBytes      有效增量额度之和
 * @param extraExpireAt   最近一笔增量到期时刻（可空）
 * @param usedBytes       已用量（含回收站）
 * @param uploadBlocked   = usedBytes > freeBytes + extraBytes（R7 推导值，不落库）
 */
public record QuotaView(
        long freeBytes,
        long extraBytes,
        OffsetDateTime extraExpireAt,
        long usedBytes,
        boolean uploadBlocked
) {
}