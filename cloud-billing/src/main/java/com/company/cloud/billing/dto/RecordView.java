package com.company.cloud.billing.dto;

import java.time.OffsetDateTime;

/**
 * 计费记录行（§4.2 records 台账项）。
 *
 * @param id             记录 id
 * @param userId         持有者 id
 * @param username       持有者用户名
 * @param gbCount        增量 GB 数
 * @param amountCents    金额（分）
 * @param appliedAt      生效时刻
 * @param expireAt       到期时刻
 * @param status         active / expired
 * @param requestId      关联申请 id
 * @param approvedBy     审批人 id
 * @param approvedByName 审批人用户名
 */
public record RecordView(
        Long id,
        Long userId,
        String username,
        int gbCount,
        long amountCents,
        OffsetDateTime appliedAt,
        OffsetDateTime expireAt,
        String status,
        Long requestId,
        Long approvedBy,
        String approvedByName
) {
}