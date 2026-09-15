package com.company.cloud.files.audit.impl;

import com.company.cloud.common.audit.AuditEvent;
import com.company.cloud.common.audit.AuditService;
import com.company.cloud.files.audit.mapper.AuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 审计落库实现（R-C08）：{@code @Async("auditExecutor")} 异步写 audit_logs，
 * 不阻塞调用方主流程。
 *
 * <p>降级原则：序列化 / 落库任何异常只 log.error，绝不向上抛出——
 * 审计不能拖垮主流程（契约：审计丢失只允许发生在极端故障场景）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Async("auditExecutor")
    public void record(AuditEvent event) {
        try {
            String detail = event.detail() == null ? null : objectMapper.writeValueAsString(event.detail());
            auditLogMapper.insertEvent(event.userId(), event.action(), event.target(), event.ip(), detail);
        } catch (Exception e) {
            log.error("[audit] 审计落库失败，已降级丢弃: action={} target={} err={}",
                    event.action(), event.target(), e.toString());
        }
    }
}
