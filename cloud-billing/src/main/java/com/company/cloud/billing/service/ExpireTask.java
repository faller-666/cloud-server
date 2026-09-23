package com.company.cloud.billing.service;

import com.company.cloud.billing.mapper.BillingRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 每日 03:00 到期收回定时任务（§4.4 + R6，幂等可重入，按批处理）：
 * <ol>
 *   <li>标记所有 active 且 expire_at&lt;=now() 的记录为 expired（条件更新，天然幂等）；</li>
 *   <li>收集本次到期（expired 且 extra_deducted=0）的去重 user_id 列表（分批）；</li>
 *   <li>逐用户通过 {@link QuotaExpireService} 在一笔事务内完成扣减+标记+重算+审计。</li>
 * </ol>
 * 幂等依据 billing_records.extra_deducted 标记：存量已过期记录在迁移时已置 1（视为已扣，
 * 存量不动），本任务只处理新到期且未扣的记录，重跑/并发不会重复扣减。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireTask {

    private final BillingRecordMapper recordMapper;
    private final QuotaExpireService quotaExpireService;

    /** 单批处理用户上限，防止一次事务/内存过大 */
    private static final int BATCH_SIZE = 200;

    @Scheduled(cron = "0 0 3 * * *")
    public void expireDueExtra() {
        OffsetDateTime now = OffsetDateTime.now();
        log.info("[billing] 到期收回任务启动 now={}", now);

        // 1. 幂等标记到期 active -> expired
        int marked = recordMapper.expireDue(now);
        if (marked <= 0) {
            log.info("[billing] 无到期记录，任务结束");
            return;
        }

        // 2. 收集本次到期、尚未扣减的用户（expired 且 extra_deducted=0；分批 LIMIT）
        List<Long> userIds = recordMapper.selectExpiredUserIds(now, BATCH_SIZE);

        // 3. 逐用户（事务内）扣减/标记/重算/审计
        int handled = 0;
        for (Long uid : userIds) {
            quotaExpireService.recoverUser(uid, now, marked);
            handled++;
        }
        log.info("[billing] 到期收回完成，本次标记 {} 条，处理用户 {} 人", marked, handled);
    }
}