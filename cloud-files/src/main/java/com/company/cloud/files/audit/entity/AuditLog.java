package com.company.cloud.files.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 审计日志（对应 audit_logs 表，Owner：C 组，见 V2001__create_audit_logs.sql）。
 *
 * <p>detail 列为 JSONB，Java 侧以 JSON 文本持有；
 * 写入须走 {@code AuditLogMapper.insertEvent}（CAST 为 jsonb），
 * MP 默认 insert 无法处理 jsonb cast。
 */
@Data
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作者用户 ID（系统任务可为 null） */
    private Long userId;

    /** 动作，见 com.company.cloud.common.audit.AuditActions */
    private String action;

    /** 操作对象（文件ID、用户名等） */
    private String target;

    /** 来源 IP */
    private String ip;

    /** 扩展信息（JSON 文本，落库为 JSONB） */
    private String detail;

    /** 执行结果（V2005 新增，现有只记成功事件，恒为 success） */
    private String status;

    private OffsetDateTime createdAt;
}
