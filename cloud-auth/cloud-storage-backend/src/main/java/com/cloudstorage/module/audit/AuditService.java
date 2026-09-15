package com.cloudstorage.module.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审计服务（对接 C 组 AuditService）
 * 事件清单（已与 C 组约定的命名）：
 *   login_success / login_fail / change_password / disable_user / quota_change ...
 * 本骨架先以日志落审计；接入真实 AuditService 时替换内部实现即可。
 */
@Slf4j
@Service
public class AuditService {

    public void loginSuccess(Long userId, String username, String ip) {
        log.info("[audit] login_success userId={} username={} ip={}", userId, username, ip);
    }

    public void loginFail(String username, String ip) {
        log.info("[audit] login_fail username={} ip={}", username, ip);
    }

    public void changePassword(Long userId, String username) {
        log.info("[audit] change_password userId={} username={}", userId, username);
    }

    public void disableUser(Long operatorId, Long targetId) {
        log.info("[audit] disable_user operator={} target={}", operatorId, targetId);
    }

    public void quotaChange(Long operatorId, Long targetId, Long oldQuota, Long newQuota) {
        log.info("[audit] quota_change operator={} target={} old={} new={}", operatorId, targetId, oldQuota, newQuota);
    }
}