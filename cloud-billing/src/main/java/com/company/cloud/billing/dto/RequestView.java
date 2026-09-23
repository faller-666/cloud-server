package com.company.cloud.billing.dto;

import java.time.OffsetDateTime;

/**
 * 增额申请行（§4.1 我的申请 List 项；§4.2 管理端待办 List 项）。
 *
 * @param id          申请 id
 * @param userId      申请人 id（用户端为当前用户）
 * @param username    申请人名（管理端展示用，用户端为本人用户名）
 * @param gbCount     申请 GB 数
 * @param amountCents 应付金额（分）
 * @param status      pending / approved / rejected
 * @param remark      备注
 * @param createdAt   提交时间
 * @param handledAt   处理时间（可空）
 */
public record RequestView(
        Long id,
        Long userId,
        String username,
        int gbCount,
        long amountCents,
        String status,
        String remark,
        OffsetDateTime createdAt,
        OffsetDateTime handledAt
) {
}