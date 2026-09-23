package com.company.cloud.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 计费全局配置（单行，id 恒为 1）。对应 billing_config。
 */
@Data
@TableName("billing_config")
public class BillingConfig {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /** 新注册用户的默认免费额度（快照制，R3：仅影响此后新用户） */
    private Long freeBytes;

    /** 增额单价：分/GB/月（金额以分存储，R4） */
    private Integer pricePerGbMonthCents;

    /** 最后修改人 users.id */
    private Long updatedBy;

    private OffsetDateTime updatedAt;
}