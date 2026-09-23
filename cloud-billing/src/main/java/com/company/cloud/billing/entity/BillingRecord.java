package com.company.cloud.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 计费记录（增量额度台账）。对应 billing_records。
 */
@Data
@TableName("billing_records")
public class BillingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 持有者 users.id */
    private Long userId;

    /** 关联申请 id，唯一（一申请一记录，uk_br_request 兜底防重） */
    private Long requestId;

    private Integer gbCount;

    /** 金额（分） */
    private Long amountCents;

    /** 生效时刻 */
    private OffsetDateTime appliedAt;

    /** 到期时刻 = applied_at + 30 天（R1 滚动周期） */
    private OffsetDateTime expireAt;

    /** active / expired */
    private String status;

    /** 审批人 users.id */
    private Long approvedBy;

    private OffsetDateTime createdAt;
}