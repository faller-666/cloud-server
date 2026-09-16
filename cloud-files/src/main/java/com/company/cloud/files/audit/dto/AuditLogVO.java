package com.company.cloud.files.audit.dto;

import com.company.cloud.files.audit.entity.AuditLog;

import java.time.OffsetDateTime;

/**
 * 审计日志视图（R-C09 查询返回前端）。detail 以 JSON 文本原样返回，由前端解析展示。
 */
public record AuditLogVO(
        Long id,
        Long userId,
        String action,
        String target,
        String ip,
        String detail,
        OffsetDateTime createdAt
) {
    public static AuditLogVO from(AuditLog log) {
        return new AuditLogVO(
                log.getId(),
                log.getUserId(),
                log.getAction(),
                log.getTarget(),
                log.getIp(),
                log.getDetail(),
                log.getCreatedAt()
        );
    }
}
