package com.company.cloud.common.audit;

import java.util.Map;

/**
 * 审计事件。动作取值见 {@link AuditActions}。
 *
 * @param userId 操作者 ID（系统任务可为 null）
 * @param action 动作，见 AuditActions 动作字典
 * @param target 操作对象（文件ID、用户名等），可为 null
 * @param ip     来源 IP，可为 null
 * @param detail 扩展信息（文件名、大小、前后值等），落库为 JSONB
 */
public record AuditEvent(Long userId, String action, String target, String ip,
                         Map<String, Object> detail) {
}
