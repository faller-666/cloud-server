package com.company.cloud.billing.service;

import com.company.cloud.billing.mapper.BillingRecordMapper;
import com.company.cloud.common.audit.AuditActions;
import com.company.cloud.common.audit.AuditEvent;
import com.company.cloud.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 到期收回的逐用户执行器（R6）。
 * 独立 @Service 以保证 @Transactional 通过 Spring 代理生效（ExpireTask 为 @Scheduled 循环调用）。
 * 每个用户一轮事务内完成：计算待扣字节 -> 扣减 extra_bytes -> 标记幂等 -> 重算到期时刻 -> 审计。
 * 幂等依据 extra_deducted 标记：任务重跑/并发都不会对同一批记录重复扣减。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaExpireService {

    private final BillingRecordMapper recordMapper;
    private final AuditService auditService;

    /**
     * 对单个到期用户执行收回。该方法整体在一个事务内，失败即整体回滚（不残留半扣减状态）。
     *
     * @param userId 到期用户
     * @param now    本次任务时间基准
     * @param marked 本批 expireDue 标记行数（仅作审计信息）
     */
    @Transactional
    public void recoverUser(Long userId, OffsetDateTime now, int marked) {
        // 1. 计算本用户"本次到期、尚未扣减"的增量额度（字节）
        long dueBytes = recordMapper.sumDueExtraBytes(userId, now);
        if (dueBytes > 0) {
            // 2. 扣减用户冗余 extra_bytes（字节，GREATEST 防负）
            recordMapper.reduceExtraBytes(userId, dueBytes);
            // 3. 标记幂等：这批到期记录视为已扣，防止重跑/并发重复扣减
            recordMapper.markDueDeducted(userId, now);
        }
        // 4. 重算 extra_expire_at = MIN(剩余 active 到期时刻)，无 active 则置 NULL
        recordMapper.recalcUserExpireAt(userId);

        // 5. 审计（系统操作，operator=null）
        auditService.record(new AuditEvent(
                null, AuditActions.BILLING_EXPIRE, String.valueOf(userId), null,
                Map.of("userId", userId, "dueBytes", dueBytes, "markedTotal", marked)));
        log.info("[billing] 到期收回用户 userId={} dueBytes={} now={}", userId, dueBytes, now);
    }
}