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
 *   <li>收集到期则待扣（expired 且 extra_deducted=0）的去重 user_id 列表（分批）；</li>
 *   <li>逐用户通过 {@link QuotaExpireService} 在一笔事务内完成扣减+标记+重算+审计。</li>
 * </ol>
 * 幂等依据 billing_records.extra_deducted 标记：存量已过期记录在迁移时已置 1（视为已扣，
 * 存量不动），本任务只处理新到期且未扣的记录，重跑/并发不会重复扣减。
 *
 * <p>修复说明：原实现「本次无新到期(marked&lt;=0)时提前 return」会导致上一批超 BATCH_SIZE
 * 未处理完的遗留用户（expired 且 extra_deducted=0）永远不被收回。现改为循环分批处理，
 * 直到没有待扣用户为止，保证到期配额最终都能收回。
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

        // 2+3. 分批收集"到期且尚未扣减"的用户（expired 且 extra_deducted=0；LIMIT 分批），
        // 并在事务内逐用户扣减/标记/重算/审计。
        // 即使本次 marked=0（无新到期），上一批超 BATCH_SIZE 未处理完的遗留用户
        // 仍是 expired+extra_deducted=0，必须继续处理，不能因 marked<=0 提前返回。
        int handled = 0;
        List<Long> userIds;
        while (!(userIds = recordMapper.selectExpiredUserIds(now, BATCH_SIZE)).isEmpty()) {
            for (Long uid : userIds) {
                quotaExpireService.recoverUser(uid, now, marked);
                handled++;
            }
        }
        if (handled == 0) {
            log.info("[billing] 无到期记录，任务结束");
        } else {
            log.info("[billing] 到期收回完成，本次标记 {} 条，处理用户 {} 人", marked, handled);
        }
    }
}