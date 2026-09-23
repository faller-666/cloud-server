package com.company.cloud.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 增额申请。对应 increase_requests。
 */
@Data
@TableName("increase_requests")
public class IncreaseRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人 users.id */
    private Long userId;

    /** 申请 GB 数（整数 1~1000） */
    private Integer gbCount;

    /** 应付金额（分，后端重算，忽略前端金额） */
    private Long amountCents;

    /** pending / approved / rejected */
    private String status;

    private String remark;

    /** 审批人 users.id */
    private Long handledBy;

    private OffsetDateTime createdAt;

    private OffsetDateTime handledAt;
}