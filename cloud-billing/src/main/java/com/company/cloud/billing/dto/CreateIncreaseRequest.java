package com.company.cloud.billing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交增额申请请求（§4.1 POST /billing/increase-requests）。
 *
 * @param gbCount 申请 GB 数（整数 1~1000，超出 → 40403）
 * @param remark  备注（可空）
 */
public record CreateIncreaseRequest(
        @NotNull(message = "gbCount 必填")
        @Min(value = 1, message = "gbCount 最小为 1")
        @Max(value = 1000, message = "gbCount 最大为 1000")
        Integer gbCount,

        @Size(max = 500, message = "remark 超长")
        String remark
) {
}