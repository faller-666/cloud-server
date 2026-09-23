package com.company.cloud.billing.dto;

/**
 * 计费配置响应（§4.1 config）。
 *
 * @param freeBytes             新用户默认免费额度（快照制）
 * @param pricePerGbMonthCents  单价（分/GB/月）
 * @param note                  管理端 PATCH 时的影响范围提示
 */
public record ConfigView(
        long freeBytes,
        int pricePerGbMonthCents,
        String note
) {
}