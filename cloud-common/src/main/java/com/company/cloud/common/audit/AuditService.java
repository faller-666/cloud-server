package com.company.cloud.common.audit;

/**
 * 审计写入服务（任务书 04 §6：提供给 A/B 组统一调用）。
 *
 * <p>要求：异步落库、不阻塞调用方主流程；调用方即使在事务中也应视为"已受理"，
 * 审计丢失只允许发生在极端故障（如 Redis 与 DB 同时不可用）场景。
 *
 * <p>使用示例：
 * <pre>{@code
 * auditService.record(new AuditEvent(
 *         userId, AuditActions.UPLOAD, String.valueOf(fileId), ip,
 *         Map.of("filename", name, "size", size)));
 * }</pre>
 */
public interface AuditService {

    /**
     * 记录一条审计日志，异步落库，立即返回。
     *
     * @param event 审计事件，action 必须取自 {@link AuditActions}
     */
    void record(AuditEvent event);
}
