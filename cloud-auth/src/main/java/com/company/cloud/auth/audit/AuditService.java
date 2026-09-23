package com.company.cloud.auth.audit;

import com.company.cloud.common.audit.AuditActions;
import com.company.cloud.common.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 审计服务（对接 C 组 AuditService）
 * 事件清单（已与 C 组约定的命名）：
 *   login_success / login_fail / change_password / disable_user / quota_change ...
 * 本类为适配器：保持 A 组业务代码零改动，内部转发到
 * {@link com.company.cloud.common.audit.AuditService} 统一异步落库。
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final com.company.cloud.common.audit.AuditService auditService;

    public void loginSuccess(Long userId, String username, String ip) {
        auditService.record(new AuditEvent(
                userId, AuditActions.LOGIN, username, ip,
                Map.of("username", username, "success", true)));
    }

    public void loginFail(String username, String ip) {
        auditService.record(new AuditEvent(
                null, AuditActions.LOGIN, username, ip,
                Map.of("username", username, "success", false)));
    }

    public void changePassword(Long userId, String username) {
        auditService.record(new AuditEvent(
                userId, AuditActions.USER_MANAGE, username, null,
                Map.of("op", "change_password", "username", username)));
    }

    public void disableUser(Long operatorId, Long targetId) {
        auditService.record(new AuditEvent(
                operatorId, AuditActions.USER_MANAGE, String.valueOf(targetId), null,
                Map.of("op", "disable_user", "targetId", targetId)));
    }

    public void quotaChange(Long operatorId, Long targetId, Long oldQuota, Long newQuota) {
        auditService.record(new AuditEvent(
                operatorId, AuditActions.QUOTA_CHANGE, String.valueOf(targetId), null,
                Map.of("targetId", targetId, "oldQuota", oldQuota, "newQuota", newQuota)));
    }

    public void demoteUser(Long operatorId, Long targetId, String oldRole, String newRole) {
        auditService.record(new AuditEvent(
                operatorId, AuditActions.USER_DEMOTE, String.valueOf(targetId), null,
                Map.of("targetId", targetId, "oldRole", oldRole, "newRole", newRole)));
    }
}
