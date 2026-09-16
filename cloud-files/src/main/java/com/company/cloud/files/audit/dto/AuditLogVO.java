package com.company.cloud.files.audit.dto;

import com.company.cloud.files.audit.entity.AuditLog;

import java.time.OffsetDateTime;

/**
 * 审计日志视图（R-C09 查询返回前端）。detail 以 JSON 文本原样返回，由前端解析展示。
 * username 由查询侧 JOIN users 补充；status 为执行结果（现有审计只记成功事件，恒为 success）。
 */
public record AuditLogVO(
        Long id,
        Long userId,
        String username,
        String action,
        String target,
        String ip,
        String detail,
        String status,
        OffsetDateTime createdAt
) {
    public static AuditLogVO from(AuditLog log) {
        return from(log, null);
    }

    public static AuditLogVO from(AuditLog log, String username) {
        return new AuditLogVO(
                log.getId(),
                log.getUserId(),
                username,
                log.getAction(),
                log.getTarget(),
                log.getIp(),
                log.getDetail(),
                log.getStatus(),
                log.getCreatedAt()
        );
    }
}
