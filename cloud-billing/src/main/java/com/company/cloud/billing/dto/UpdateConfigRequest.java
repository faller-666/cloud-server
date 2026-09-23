package com.company.cloud.billing.dto;

import jakarta.validation.constraints.Min;

/**
 * 修改计费配置请求（§4.2 PATCH /admin/billing/config）。
 * 字段均可选，至少一项；值为负 → 40001。
 *
 * @param freeBytes             新用户默认免费额度（快照制，仅影响此后新用户）
 * @param pricePerGbMonthCents  单价（分/GB/月）
 */
public record UpdateConfigRequest(
        @Min(value = 0, message = "freeBytes 不能为负")
        Long freeBytes,

        @Min(value = 0, message = "pricePerGbMonthCents 不能为负")
        Integer pricePerGbMonthCents
) {
    public boolean isEmpty() {
        return freeBytes == null && pricePerGbMonthCents == null;
    }
}