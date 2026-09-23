package com.company.cloud.billing.service;

import com.company.cloud.billing.mapper.BillingRecordMapper;
import com.company.cloud.billing.mapper.UserQuotaMapper;
import com.company.cloud.common.audit.AuditActions;
import com.company.cloud.common.audit.AuditEvent;
import com.company.cloud.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 每日 03:00 到期收回定时任务（§4.4 + R6，幂等可重入）：
 * <ol>
 *   <li>标记所有 active 且 expire_at&lt;=now() 的记录为 expired（条件更新，天然幂等）；</li>
 *   <li>收集去重 user_id 列表（分批）；</li>
 *   <li>逐用户：扣减到期的 extra_bytes = GREATEST(extra_bytes - 到期gb_sum, 0)；
 *       重算 extra_expire_at = MIN(剩余 active 记录 expire_at)，无 active 则置 NULL；</li>
 *   <li>审计 billing_expire（操作人为系统 null）。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireTask {

    private final BillingRecordMapper recordMapper;
    private final UserQuotaMapper userQuotaMapper;
    private final AuditService auditService;

    /** 单批处理用户上限，防止一次事务/内存过大 */
    private static final int BATCH_SIZE = 200;

    @Scheduled(cron = "0 0 3 * * *")
    public void expireDueExtra() {
        OffsetDateTime now = OffsetDateTime.now();
        log.info("[billing] 到期收回任务启动 now={}", now);

        // 1. 幂等标记到期
        int marked = recordMapper.expireDue(now);
        if (marked <= 0) {
            log.info("[billing] 无到期记录，任务结束");
            return;
        }

        // 2. 收集受影响用户（去重、分批）
        List<Long> userIds = recordMapper.selectExpiredUserIds(now, BATCH_SIZE);

        // 3. 逐用户收敛
        for (Long uid : userIds) {
            // 扣减到期部分 extra_bytes（GREATEST 防负）
            recordMapper.deductExpiredExtra(uid, now);
            // 重算 extra_expire_at：MIN(剩余 active 到期时间)，无 active 则置 NULL
            recordMapper.recalcUserExpireAt(uid);
            // 审计（系统操作，operator=null；detail 附到期影响行数）
            auditService.record(new AuditEvent(
                    null, AuditActions.BILLING_EXPIRE, String.valueOf(uid), null,
                    Map.of("userId", uid, "markedTotal", marked)));
        }
        log.info("[billing] 到期收回完成，处理用户 {} 人", userIds.size());
    }
}